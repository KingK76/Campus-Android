package de.tum.in.tumcampusapp.component.ui.tufilm;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Provider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import de.tum.in.tumcampusapp.R;
import de.tum.in.tumcampusapp.component.other.generic.activity.BaseActivity;
import de.tum.in.tumcampusapp.component.ui.ticket.EventHelper;
import de.tum.in.tumcampusapp.component.ui.ticket.activity.ShowTicketActivity;
import de.tum.in.tumcampusapp.component.ui.ticket.model.Event;
import de.tum.in.tumcampusapp.component.ui.ticket.repository.TicketsLocalRepository;
import de.tum.in.tumcampusapp.component.ui.tufilm.di.KinoModule;
import de.tum.in.tumcampusapp.component.ui.tufilm.model.Kino;
import de.tum.in.tumcampusapp.di.ViewModelFactory;
import de.tum.in.tumcampusapp.utils.Const;

import static de.tum.in.tumcampusapp.utils.Const.KEY_EVENT_ID;

/**
 * Fragment for KinoDetails. Manages content that gets shown on the pagerView
 */
public class KinoDetailsFragment extends Fragment {

    private View rootView;
    private Event event;

    @Inject
    Provider<KinoDetailsViewModel> viewModelProvider;

    @Inject
    TicketsLocalRepository ticketsLocalRepo;

    private KinoDetailsViewModel kinoViewModel;

    public static KinoDetailsFragment newInstance(int position) {
        KinoDetailsFragment fragment = new KinoDetailsFragment();
        Bundle args = new Bundle();
        args.putInt(Const.POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        ((BaseActivity) requireActivity()).getInjector()
                .kinoComponent()
                .kinoModule(new KinoModule())
                .build()
                .inject(this);

        ViewModelFactory<KinoDetailsViewModel> factory = new ViewModelFactory<>(viewModelProvider);
        kinoViewModel = ViewModelProviders.of(this, factory).get(KinoDetailsViewModel.class);

        kinoViewModel.getKino().observe(this, this::showMovieDetails);
        kinoViewModel.getEvent().observe(this, this::showEventTicketDetails);
        kinoViewModel.getTicketCount().observe(this, this::showTicketCount);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_kinodetails_section, container, false);
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (event != null) {
            initBuyOrShowTicket(event);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final int position = getArguments().getInt(Const.POSITION);
        kinoViewModel.fetchKinoByPosition(position);
    }

    private void showEventTicketDetails(Event event) {
        this.event = event;
        initBuyOrShowTicket(event);

        rootView.findViewById(R.id.eventInformation).setVisibility(View.VISIBLE);
        ((TextView) rootView.findViewById(R.id.locationTextView)).setText(event.getLocality());

        kinoViewModel.fetchTicketCount(event.getId());
    }

    private void initBuyOrShowTicket(Event event) {
        MaterialButton ticketButton = rootView.findViewById(R.id.buyTicketButton);
        if (ticketsLocalRepo.isEventBooked(event)) {
            ticketButton.setText(R.string.show_ticket);
            ticketButton.setVisibility(View.VISIBLE);
            ticketButton.setOnClickListener(view -> {
                Intent intent = new Intent(getContext(), ShowTicketActivity.class);
                intent.putExtra(KEY_EVENT_ID, event.getId());
                startActivity(intent);
            });
        } else if (!EventHelper.Companion.isEventImminent(event)) {
            ticketButton.setText(R.string.buy_ticket);
            ticketButton.setVisibility(View.VISIBLE);
            ticketButton.setOnClickListener(
                    view -> EventHelper.Companion.buyTicket(this.event, ticketButton, getContext()));
        }
    }

    private void showTicketCount(@Nullable Integer count) {
        String text;

        if (count != null) {
            text = String.format(Locale.getDefault(), "%d", count);
        } else {
            text = getString(R.string.unknown);
        }

        ((TextView) rootView.findViewById(R.id.remainingTicketsTextView)).setText(text);
    }

    private void showMovieDetails(Kino kino) {
        kinoViewModel.fetchEventByMovieId(kino.getId());

        loadPoster(kino);

        TextView dateTextView = rootView.findViewById(R.id.dateTextView);
        dateTextView.setText(kino.getFormattedShortDate());

        TextView runtimeTextView = rootView.findViewById(R.id.runtimeTextView);
        runtimeTextView.setText(kino.getRuntime());

        TextView ratingTextView = rootView.findViewById(R.id.ratingTextView);
        ratingTextView.setText(kino.getFormattedRating());

        int colorPrimary = ContextCompat.getColor(requireContext(), R.color.color_primary);
        setCompoundDrawablesTint(dateTextView, colorPrimary);
        setCompoundDrawablesTint(runtimeTextView, colorPrimary);
        setCompoundDrawablesTint(ratingTextView, colorPrimary);

        TextView descriptionTextView = rootView.findViewById(R.id.descriptionTextView);
        descriptionTextView.setText(kino.getFormattedDescription());

        TextView genresTextView = rootView.findViewById(R.id.genresTextView);
        genresTextView.setText(kino.getGenre());

        TextView releaseYearTextView = rootView.findViewById(R.id.releaseYearTextView);
        releaseYearTextView.setText(kino.getYear());

        TextView actorsTextView = rootView.findViewById(R.id.actorsTextView);
        actorsTextView.setText(kino.getActors());

        TextView directorTextView = rootView.findViewById(R.id.directorTextView);
        directorTextView.setText(kino.getDirector());

        MaterialButton moreInfoButton = rootView.findViewById(R.id.moreInfoButton);
        moreInfoButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(kino.getLink()));
            startActivity(intent);
        });
    }

    private void loadPoster(Kino kino) {
        MaterialButton trailerButton = rootView.findViewById(R.id.trailerButton);
        trailerButton.setOnClickListener(v -> showTrailer(kino));

        ImageView posterView = rootView.findViewById(R.id.kino_cover);
        ProgressBar progressBar = rootView.findViewById(R.id.kino_cover_progress);

        Picasso.get()
                .load(kino.getCover())
                .into(new Target() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        progressBar.setVisibility(View.GONE);
                        posterView.setImageBitmap(bitmap);
                    }

                    @Override
                    public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                        progressBar.setVisibility(View.GONE);
                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {
                        // Free ad space
                    }
                });
    }

    private void setCompoundDrawablesTint(TextView textView, int color) {
        for (Drawable drawable : textView.getCompoundDrawables()) {
            if (drawable != null) {
                drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
        }
    }

    private void showTrailer(Kino kino) {
        String url = kino.getTrailerSearchUrl();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        requireActivity().startActivity(intent);
    }

}
