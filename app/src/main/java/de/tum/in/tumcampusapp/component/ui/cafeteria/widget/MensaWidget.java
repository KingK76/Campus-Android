package de.tum.in.tumcampusapp.component.ui.cafeteria.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;

import javax.inject.Inject;

import de.tum.in.tumcampusapp.App;
import de.tum.in.tumcampusapp.R;
import de.tum.in.tumcampusapp.component.ui.cafeteria.activity.CafeteriaActivity;
import de.tum.in.tumcampusapp.component.ui.cafeteria.controller.CafeteriaManager;
import de.tum.in.tumcampusapp.component.ui.cafeteria.di.CafeteriaModule;
import de.tum.in.tumcampusapp.component.ui.cafeteria.model.Cafeteria;
import de.tum.in.tumcampusapp.component.ui.cafeteria.repository.CafeteriaLocalRepository;
import de.tum.in.tumcampusapp.service.MensaWidgetService;
import de.tum.in.tumcampusapp.utils.Const;

/**
 * Implementation of Mensa Widget functionality.
 * The Update intervals is set to 10 hours in mensa_widget_info.xml
 */
public class MensaWidget extends AppWidgetProvider {

    @Inject
    CafeteriaManager cafeteriaManager;

    @Inject
    CafeteriaLocalRepository localRepository;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        // TODO(thellmund) Search for a more efficient way, e.g. AndroidInjection
        ((App) context.getApplicationContext()).getAppComponent()
                .cafeteriaComponent()
                .cafeteriaModule(new CafeteriaModule(context))
                .build()
                .inject(this);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void updateAppWidget(Context context,
                                 AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.mensa_widget);

        int cafeteriaId = cafeteriaManager.getBestMatchMensaId();
        Cafeteria cafeteria = localRepository.getCafeteria(cafeteriaId);

        // TODO: Investigate how this can be null
        if (cafeteria != null) {
            // Set the header for the Widget layout
            rv.setTextViewText(R.id.mensa_widget_header, cafeteria.getName());
        }

        // Set the properly formatted date in the subhead
        LocalDate localDate = DateTime.now().toLocalDate();
        String date = DateTimeFormat.shortDate().print(localDate);
        rv.setTextViewText(R.id.mensa_widget_subhead, date);

        // Set the header on click to open the mensa activity
        Intent mensaIntent = new Intent(context, CafeteriaActivity.class);
        mensaIntent.putExtra(Const.CAFETERIA_ID, cafeteriaManager.getBestMatchMensaId());
        PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, mensaIntent, 0);
        rv.setOnClickPendingIntent(R.id.mensa_widget_header_container, pendingIntent);

        // Set the adapter for the list view in the mensa widget
        Intent intent = new Intent(context, MensaWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        rv.setRemoteAdapter(R.id.food_item, intent);
        rv.setEmptyView(R.id.empty_view, R.id.empty_view);

        appWidgetManager.updateAppWidget(appWidgetId, rv);
    }

}


