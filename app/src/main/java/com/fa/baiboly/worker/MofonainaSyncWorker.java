package com.fa.baiboly.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.fa.baiboly.data.MofonainaRepository;
import com.fa.baiboly.models.MofonainaData;
import com.fa.baiboly.widgets.MofonainaOnlineWidget;

public class MofonainaSyncWorker extends Worker {

    public MofonainaSyncWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {

        try {

            MofonainaRepository repository =
                    new MofonainaRepository(getApplicationContext());

            repository.sync("https://www.fjkm.mg");

            MofonainaOnlineWidget.refresh(getApplicationContext());

            return Result.success();

        } catch (Exception e) {

            e.printStackTrace();

            return Result.retry();
        }
    }
}