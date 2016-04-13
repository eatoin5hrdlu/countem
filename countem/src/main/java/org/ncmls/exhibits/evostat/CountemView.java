package org.ncmls.exhibits.countem;

import java.io.FileOutputStream;
import java.util.List;

import org.opencv.android.JavaCameraView;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.Log;

import static android.app.PendingIntent.getActivity;

public class CountemView extends JavaCameraView implements PictureCallback {

    private static final String TAG = "CountemView";
    private String mPictureFileName;

    public CountemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.i(TAG,"CountemView constructor called");

    }

    public List<String> getEffectList() {
        if (mCamera == null) {
            connectCamera(getWidth(), getHeight());
        }
        return mCamera.getParameters().getSupportedColorEffects();
    }

    public boolean isEffectSupported() {
        if (mCamera == null) {
            connectCamera(getWidth(), getHeight());
        }
        return (mCamera.getParameters().getColorEffect() != null);
    }

    public String getEffect() {
        return mCamera.getParameters().getColorEffect();
    }

    public void setEffect(String effect) {
        Camera.Parameters params = mCamera.getParameters();
        params.setColorEffect(effect);
        mCamera.setParameters(params);
    }
    public void setAuto(boolean z) {
        Camera.Parameters params = mCamera.getParameters();
        params.setAutoExposureLock(z);
        params.setSceneMode(params.SCENE_MODE_NIGHT);
        mCamera.setParameters(params);
        Log.i(TAG,"Set AutoExposureLock to "+ z);
    }

    public List<Size> getResolutionList() {
        return mCamera.getParameters().getSupportedPreviewSizes();
    }

    public void setResolution(Size resolution) {
        disconnectCamera();
        mMaxHeight = resolution.height;
        mMaxWidth = resolution.width;
        connectCamera(getWidth(), getHeight());
    }

    public Size getResolution() {
        return mCamera.getParameters().getPreviewSize();
    }

    public void takePicture(final String fileName) {
        Log.i(TAG, "Taking picture");
        this.mPictureFileName = fileName;

        // Postview and jpeg are sent in the same buffers if the queue is not empty when performing a capture.
        // Clear up buffers to avoid mCamera.takePicture to be stuck because of a memory issue
        mCamera.setPreviewCallback(null);

        // PictureCallback is implemented by the current class
        try {
            mCamera.takePicture(null, null, this);
        } catch(RuntimeException re) {
            Log.e(TAG,"I caught this: "+ re.toString());
            ((Activity) getContext()).finish();
           // System.exit(3);
        }
        setAuto(true); //Auto Exposure off (Exposure Compensation = 0)
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        Log.i(TAG, "Saving a bitmap to file");
        // The camera preview was automatically stopped. Start it again.
        mCamera.startPreview();
        mCamera.setPreviewCallback(this);

        // Write the image in a file (in jpeg format)
        try {
            FileOutputStream fos = new FileOutputStream(mPictureFileName);

            fos.write(data);
            fos.close();

        } catch (java.io.IOException e) {
            Log.e("PictureDemo", "Exception in photoCallback", e);
        }
        //setZoom(2);
    }
}
