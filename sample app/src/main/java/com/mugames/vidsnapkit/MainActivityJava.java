package com.mugames.vidsnapkit;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.mugames.vidsnapkit.dataholders.Error;
import com.mugames.vidsnapkit.dataholders.Formats;
import com.mugames.vidsnapkit.dataholders.Result;
import com.mugames.vidsnapkit.extractor.Extractor;

/**
 * @author Udhaya
 * Created on 04-08-2022
 */

public class MainActivityJava extends AppCompatActivity {
    private static final String TAG = "MainActivityJava";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        onClick();

        findViewById(R.id.button).setOnClickListener(v->{
            onClick();
        });
    }

    private ProgressCallback progressCallback = new ProgressCallback() {
        @Override
        public void onProgress(@NonNull Result result) {
            if (result instanceof Result.Success){
                for (Formats format : ((Result.Success) result).getFormats()) {
                    Log.d(TAG, "onClick: "+format);
                }
            }else if (result instanceof Result.Failed){
                Log.e(TAG, "onClick: "+((Result.Failed) result).getError());
                if (((Result.Failed) result).getError() instanceof Error.LoginInRequired){
                    Log.e(TAG, "Add cookies: ");
                }else if (((Result.Failed) result).getError() instanceof Error.InternalError){
                    Log.e(TAG, "error = : "+ ((Result.Failed) result).getError());
                }else if (((Result.Failed) result).getError() instanceof Error.NonFatalError){
                    Log.e(TAG, "error = : "+((Result.Failed) result).getError().getMessage());
                }else if (((Result.Failed) result).getError() instanceof Error.InvalidUrl){
                    Log.e(TAG, "URL problem: ");
                }else if (((Result.Failed) result).getError() instanceof Error.NetworkError){
                    Log.e(TAG, "Check your connection: ");
                }else if (((Result.Failed) result).getError() instanceof Error.InvalidCookies){
                    Log.e(TAG, "Check your cookies: ");
                }else if (((Result.Failed) result).getError() instanceof Error.Instagram404Error){
                    Log.e(TAG, "404 and cookies used = " + ((Error.Instagram404Error)((Result.Failed) result).getError()).isCookiesUsed());
                }
            }else{
                //Used to update UI
                Log.d(TAG, "onProgress: "+((Result.Progress)result).getProgressState());
            }
        }
    };

    private void onClick(){
        String url = "FACEBOOK_INSTA_LINKEDIN_SHARECHAT_URL";
        Extractor extractor = Extractor.Companion.findExtractor(url);
//        extractor.setCookies("REQUIRED_COOKIES");
        if (extractor != null) {
            //Don't use other kind of start() from extractor for Java
            extractor.startAsync(progressCallback);
        }
    }
}
