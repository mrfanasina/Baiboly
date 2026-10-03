package com.fa.baiboly.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.fa.baiboly.MainActivity;
import com.fa.baiboly.R;
import com.fa.baiboly.data.reading.ReadingService;
import com.fa.baiboly.ui.verses.VersesActivity;

public class MofonainaOfflineWidget
        extends AppWidgetProvider {

    static void updateAppWidget(
            Context context,
            AppWidgetManager appWidgetManager,
            int appWidgetId
    ) {

        ReadingService readingService =
                new ReadingService(context);

        String verse =
                readingService.getTodayReading();

        if (verse == null || verse.isEmpty()) {
            verse = "...";
        }

        RemoteViews views =
                new RemoteViews(
                        context.getPackageName(),
                        R.layout.mofonaina_offline_widget
                );

        views.setTextViewText(
                R.id.appwidget_text,
                verse
        );

        Intent intent =
                new Intent(
                        context,
                        VersesActivity.class
                );

        intent.putExtra(
                "reading",
                verse
        );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );
        views.setOnClickPendingIntent(
                R.id.widget_root,
                pendingIntent
        );
        appWidgetManager.updateAppWidget(
                appWidgetId,
                views
        );
    }

    @Override
    public void onUpdate(
            Context context,
            AppWidgetManager appWidgetManager,
            int[] appWidgetIds
    ) {

        for (int appWidgetId : appWidgetIds) {

            updateAppWidget(
                    context,
                    appWidgetManager,
                    appWidgetId
            );
        }
    }
}