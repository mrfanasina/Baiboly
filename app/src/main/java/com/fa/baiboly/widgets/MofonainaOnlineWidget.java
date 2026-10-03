package com.fa.baiboly.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.fa.baiboly.R;
import com.fa.baiboly.data.MofonainaRepository;
import com.fa.baiboly.models.MofonainaData;
import com.fa.baiboly.ui.mofonaina.MofonainaActivity;

public class MofonainaOnlineWidget extends AppWidgetProvider {

    private static void updateWidget(
            Context context,
            AppWidgetManager appWidgetManager,
            int appWidgetId
    ) {

        new Thread(() -> {

            RemoteViews views = new RemoteViews(
                    context.getPackageName(),
                    R.layout.mofonaina_online_widget
            );

            try {

                MofonainaRepository repository =
                        new MofonainaRepository(context);

                MofonainaData data =
                        repository.getFromCache();
                if (data != null) {

                    // Titre
                    views.setTextViewText(
                            R.id.widget_title,
                            data.getTitle()
                    );

                    // Date
                    views.setTextViewText(
                            R.id.widget_date,
                            data.getDate()
                    );

                    // Verset du jour
                    if (data.getVerseOfDay() != null) {
                        views.setTextViewText(
                                R.id.widget_verse,
                                data.getVerseOfDay().toString()
                        );
                    } else {
                        views.setTextViewText(
                                R.id.widget_verse,
                                "-"
                        );
                    }

                    // Lecture biblique
                    if (data.getBibleReading1() != null) {
                        views.setTextViewText(
                                R.id.widget_reading,
                                data.getBibleReading1().toString()
                        );
                    } else {
                        views.setTextViewText(
                                R.id.widget_reading,
                                "-"
                        );
                    }

                    // Fihirana
                    if (data.getSong1() != null) {

                        String songName =
                                data.getSong1()
                                        .getCategory()
                                        .toUpperCase()
                                        + " "
                                        + data.getSong1().getNumber();

                        views.setTextViewText(
                                R.id.widget_song,
                                songName
                        );

                    } else {

                        views.setTextViewText(
                                R.id.widget_song,
                                "-"
                        );
                    }
                    if (data.getSong2() != null) {

                        String song2 =
                                data.getSong2().getCategory().toUpperCase()
                                        + " "
                                        + data.getSong2().getNumber();

                        views.setTextViewText(
                                R.id.widget_song2,
                                song2
                        );

                    } else {

                        views.setTextViewText(
                                R.id.widget_song2,
                                "-"
                        );
                    }

                }

            } catch (Exception e) {

                views.setTextViewText(
                        R.id.widget_title,
                        "Mofonaina"
                );

                views.setTextViewText(
                        R.id.widget_date,
                        "Hadisoana amin'ny fampidirana"
                );

                views.setTextViewText(
                        R.id.widget_verse,
                        ""
                );

                views.setTextViewText(
                        R.id.widget_reading,
                        ""
                );

                views.setTextViewText(
                        R.id.widget_song,
                        ""
                );

                e.printStackTrace();
            }

            // Ouvrir l'activité au clic
            Intent intent =
                    new Intent(
                            context,
                            MofonainaActivity.class
                    );

            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            context,
                            0,
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

        }).start();
    }

    @Override
    public void onUpdate(
            Context context,
            AppWidgetManager appWidgetManager,
            int[] appWidgetIds
    ) {

        for (int appWidgetId : appWidgetIds) {
            updateWidget(
                    context,
                    appWidgetManager,
                    appWidgetId
            );
        }
    }

    public static void refresh(Context context) {

        AppWidgetManager manager =
                AppWidgetManager.getInstance(context);

        ComponentName componentName =
                new ComponentName(
                        context,
                        MofonainaOnlineWidget.class
                );

        int[] ids =
                manager.getAppWidgetIds(componentName);

        for (int id : ids) {
            updateWidget(
                    context,
                    manager,
                    id
            );
        }
    }
}