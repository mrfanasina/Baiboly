package com.fa.baiboly.ui.baiboly;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class BaibolyViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public BaibolyViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("Baiboly");
    }

    public LiveData<String> getText() {
        return mText;
    }
}