package de.tum.in.tumcampusapp.component.ui.ticket.activity;

import android.content.Intent;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import de.tum.in.tumcampusapp.R;
import de.tum.in.tumcampusapp.api.app.TUMCabeClient;
import de.tum.in.tumcampusapp.api.app.model.TUMCabeVerification;
import de.tum.in.tumcampusapp.component.other.generic.activity.BaseActivity;
import de.tum.in.tumcampusapp.component.ui.ticket.di.TicketsModule;
import de.tum.in.tumcampusapp.component.ui.ticket.model.Event;
import de.tum.in.tumcampusapp.component.ui.ticket.model.TicketType;
import de.tum.in.tumcampusapp.component.ui.ticket.payload.TicketReservation;
import de.tum.in.tumcampusapp.component.ui.ticket.payload.TicketReservationResponse;
import de.tum.in.tumcampusapp.component.ui.ticket.repository.EventsLocalRepository;
import de.tum.in.tumcampusapp.component.ui.ticket.repository.TicketsRemoteRepository;
import de.tum.in.tumcampusapp.utils.Const;
import de.tum.in.tumcampusapp.utils.Utils;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * This activity shows an overview of the available tickets and a selection of all ticket types
 * Directs the user to the PaymentConfirmationActivity or back to EventDetailsActivity
 */
public class BuyTicketActivity extends BaseActivity {

    private int eventId;

    private Spinner ticketTypeSpinner;
    private FrameLayout loadingLayout;
    private Button paymentButton;

    private List<TicketType> ticketTypes;

    @Inject
    TicketsRemoteRepository ticketsRemoteRepo;

    @Inject
    EventsLocalRepository eventsLocalRepo;

    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    public BuyTicketActivity() {
        super(R.layout.activity_buy_ticket);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        eventId = getIntent().getIntExtra(Const.KEY_EVENT_ID, 0);

        getInjector().ticketsComponent()
                .ticketsModule(new TicketsModule())
                .eventId(eventId)
                .build()
                .inject(this);

        // Get ticket type information from API
        Disposable disposable = ticketsRemoteRepo.fetchTicketTypesForEvent(eventId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(Utils::log)
                .subscribe(this::handleTicketTypesDownloadSuccess, throwable -> {
                    Utils.showToast(BuyTicketActivity.this, R.string.error_something_wrong);
                    finish();
                });
        compositeDisposable.add(disposable);
    }

    private void handleTicketTypesDownloadSuccess(@NonNull List<TicketType> ticketTypes) {
        this.ticketTypes = ticketTypes;
        setupUi();
    }

    private void setupUi() {
        initEventTextViews();
        initTicketTypeSpinner();

        loadingLayout = findViewById(R.id.loading_layout);
        loadingLayout.setVisibility(View.GONE);

        paymentButton = findViewById(R.id.paymentButton);
        paymentButton.setOnClickListener(v -> reserveTicket());
    }

    private void initEventTextViews() {
        TextView eventView = findViewById(R.id.ticket_details_event);
        TextView locationView = findViewById(R.id.ticket_details_location);
        TextView dateView = findViewById(R.id.ticket_details_date);

        Event event = eventsLocalRepo.getEventById(eventId);

        eventView.setText(event.getTitle());
        locationView.setText(event.getLocality());

        String formattedStartTime = event.getFormattedStartDateTime(this);
        dateView.setText(formattedStartTime);
    }

    private void initTicketTypeSpinner() {
        ticketTypeSpinner = findViewById(R.id.ticket_type_spinner);
        ticketTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String ticketTypeName = (String) parent.getItemAtPosition(position);
                setTicketTypeInformation(ticketTypeName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing here for now
            }
        });

        ArrayList<String> ticketTypeNames = new ArrayList<>();
        for (TicketType ticketType : ticketTypes) {
            ticketTypeNames.add(ticketType.getDescription());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, ticketTypeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ticketTypeSpinner.setAdapter(adapter);
    }

    private TicketType getTicketTypeForName(String ticketTypeName) {
        for (TicketType ticketType : ticketTypes) {
            if (ticketType.getDescription().equals(ticketTypeName)) {
                return ticketType;
            }
        }
        return null;
    }

    private void setTicketTypeInformation(String ticketTypeName) {
        TextView priceView = findViewById(R.id.ticket_details_price);
        TicketType ticketType = getTicketTypeForName(ticketTypeName);
        String priceString = ticketType == null ? getString(R.string.not_valid) : ticketType.getFormattedPrice();

        priceView.setText(priceString);
    }

    private void reserveTicket() {
        TicketType ticketType = getTicketTypeForName((String) ticketTypeSpinner.getSelectedItem());
        if (ticketType == null) {
            Utils.showToast(this, R.string.internal_error);
            return;
        }

        loadingLayout.setVisibility(View.VISIBLE);
        TransitionManager.beginDelayedTransition(loadingLayout);
        paymentButton.setEnabled(false);

        int ticketTypeId = ticketType.getId();
        TicketReservation reservation = new TicketReservation(ticketTypeId);

        TUMCabeVerification verification = TUMCabeVerification.create(this, reservation);
        if (verification == null) {
            handleTicketReservationFailure(R.string.internal_error);
            return;
        }

        TUMCabeClient
                .getInstance(this)
                .reserveTicket(verification, new Callback<TicketReservationResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<TicketReservationResponse> call,
                                           @NonNull Response<TicketReservationResponse> response) {
                        // ResponseBody can be null if the user has already bought a ticket
                        // but has not fetched it from the server yet
                        TicketReservationResponse reservationResponse = response.body();
                        if (response.isSuccessful()
                                && reservationResponse != null
                                && reservationResponse.getError() == null) {
                            handleTicketReservationSuccess(ticketType, reservationResponse);
                        } else {
                            if (reservationResponse == null || !response.isSuccessful()) {
                                handleTicketNotFetched();
                            } else {
                                handleTicketReservationFailure(R.string.event_imminent_error);
                                finish();
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<TicketReservationResponse> call,
                                          @NonNull Throwable t) {
                        Utils.log(t);
                        handleTicketReservationFailure(R.string.error_something_wrong);
                    }
                });
    }

    private void handleTicketReservationSuccess(TicketType ticketType,
                                                TicketReservationResponse response) {
        loadingLayout.setVisibility(View.GONE);
        TransitionManager.beginDelayedTransition(loadingLayout);

        paymentButton.setEnabled(true);

        Intent intent = new Intent(this, StripePaymentActivity.class);
        intent.putExtra(Const.KEY_TICKET_PRICE, ticketType.getFormattedPrice());
        intent.putExtra(Const.KEY_TICKET_HISTORY, response.getTicketHistory());
        intent.putExtra(Const.KEY_TERMS_LINK, ticketType.getPaymentInfo().getTermsLink());
        intent.putExtra(Const.KEY_STRIPE_API_PUBLISHABLE_KEY, ticketType.getPaymentInfo().getStripePublicKey());
        startActivity(intent);
    }

    private void handleTicketNotFetched() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.error))
                .setMessage(getString(R.string.ticket_not_fetched))
                .setPositiveButton(R.string.ok, (dialogInterface, which) -> {
                    loadingLayout.setVisibility(View.GONE);
                    TransitionManager.beginDelayedTransition(loadingLayout);
                    paymentButton.setEnabled(true);
                })
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.rounded_corners_background);
        }

        dialog.show();
    }

    private void handleTicketReservationFailure(int messageResId) {
        loadingLayout.setVisibility(View.GONE);
        TransitionManager.beginDelayedTransition(loadingLayout);

        paymentButton.setEnabled(true);
        Utils.showToast(this, messageResId);
    }

    @Override
    protected void onDestroy() {
        compositeDisposable.dispose();
        super.onDestroy();
    }
}

