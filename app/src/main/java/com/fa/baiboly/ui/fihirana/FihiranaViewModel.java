package com.fa.baiboly.ui.fihirana;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class FihiranaViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public FihiranaViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("Fihirana");
    }

    public LiveData<String> getText() {
        return mText;
    }
}