package com.fa.baiboly.ui.home;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fa.baiboly.data.reading.ReadingService;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<String> readingText = new MutableLiveData<>();
    private String currentReading = "";

    public LiveData<String> getText() {
        return readingText;
    }

    public String getCurrentReading() {
        return currentReading;
    }

    // =========================
    // Charger lecture du jour
    // =========================
    public void loadTodayReading(Context context) {

        ReadingService readingService = new ReadingService(context);

        currentReading = readingService.getTodayReading();

        readingText.setValue(currentReading);
    }
}