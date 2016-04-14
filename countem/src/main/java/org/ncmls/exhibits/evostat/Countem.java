package org.ncmls.exhibits.countem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.CvType;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;

import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Point;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.features2d.KeyPoint;
import org.opencv.imgproc.Imgproc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import static org.ncmls.exhibits.countem.R.layout.countem_surface_view;

public class Countem extends ActionBarActivity implements CvCameraViewListener2, OnTouchListener, SeekBar.OnSeekBarChangeListener {
    private static final String TAG = "Countem";

    private CountemView mOpenCvCameraView;
    private ViewFlipper viewFlipper;
    private List<Size> mResolutionList;
    private MenuItem[] mEffectMenuItems;
    private SubMenu mColorEffectsMenu;
    private MenuItem[] mResolutionMenuItems;
    private SubMenu mResolutionMenu;

    // Lower and Upper bounds for range checking in HSV color space
    private Scalar mLowerBound = new Scalar(10);
    private Scalar mUpperBound = new Scalar(300);
    // Minimum contour area in percent for contours filtering
    private static double mMinContourArea = 0.1;
    private Mat mSpectrum;
    private Mat savedImage;
    private boolean firsttime = true;
    // Color radius for range checking in HSV color space
    private Scalar mColorRadius = new Scalar(30,50,50,0);
    private List<MatOfPoint> mContours = new ArrayList<MatOfPoint>();

    private int H;
    private int S;
    private int V;
    private SeekBar HBar;
    private TextView hbTextProgress,hbTextAction;
    private SeekBar SBar;
    private TextView sbTextProgress,sbTextAction;
    private SeekBar VBar;
    private TextView vbTextProgress,vbTextAction;
    private long lastTouched = -1;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(Countem.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public Countem() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        H = 60;
        S = 50;
        V = 50;

        setContentView( R.layout.countem_surface_view);
        viewFlipper = (ViewFlipper) findViewById(R.id.viewflipper);

        mOpenCvCameraView = (CountemView) findViewById(R.id.countem_java_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
        if (mOpenCvCameraView != null) {
            Log.i(TAG,"mOpenCVCameraView is NOT null");
        }

        HBar=(SeekBar) findViewById(R.id.HBar);
        if (HBar != null)
            HBar.setOnSeekBarChangeListener(this);
        hbTextProgress = (TextView)  findViewById(R.id.hbTextViewProgress);
        hbTextAction = (TextView) findViewById(R.id.hbTextViewAction);

    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mOpenCvCameraView == null) {
            Log.i(TAG,"mOpenCvCameraView IS null!");
        }
        List<String> effects = mOpenCvCameraView.getEffectList();

        if (effects == null) {
            Log.e(TAG, "Color effects are not supported by device!");
            return true;
        }

        mColorEffectsMenu = menu.addSubMenu("Color Effect");
        mEffectMenuItems = new MenuItem[effects.size()];

        int idx = 0;
        ListIterator<String> effectItr = effects.listIterator();
        while(effectItr.hasNext()) {
           String element = effectItr.next();
           mEffectMenuItems[idx] = mColorEffectsMenu.add(1, idx, Menu.NONE, element);
           idx++;
        }

        mResolutionMenu = menu.addSubMenu("Resolution");
        mResolutionList = mOpenCvCameraView.getResolutionList();
        mResolutionMenuItems = new MenuItem[mResolutionList.size()];

        ListIterator<Size> resolutionItr = mResolutionList.listIterator();
        idx = 0;
        while(resolutionItr.hasNext()) {
            Size element = resolutionItr.next();
            mResolutionMenuItems[idx] = mResolutionMenu.add(2, idx, Menu.NONE,
                    Integer.valueOf(element.width).toString() + "x" + Integer.valueOf(element.height).toString());
            idx++;
         }

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item.getGroupId() == 1)
        {
            mOpenCvCameraView.setEffect((String) item.getTitle());
            Toast.makeText(this, mOpenCvCameraView.getEffect(), Toast.LENGTH_SHORT).show();
        }
        else if (item.getGroupId() == 2)
        {
            int id = item.getItemId();
            Size resolution = mResolutionList.get(id);
            mOpenCvCameraView.setResolution(resolution);
            resolution = mOpenCvCameraView.getResolution();
            String caption = Integer.valueOf(resolution.width).toString() + "x" + Integer.valueOf(resolution.height).toString();
            Toast.makeText(this, caption, Toast.LENGTH_SHORT).show();
        }

        return true;
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mOpenCvCameraView.setAuto(true);
        lastTouched = System.currentTimeMillis();
        // Handling left to right screen swap.
        /*
        if (viewFlipper.getDisplayedChild() == 0) {
            viewFlipper.setInAnimation(this, R.anim.slide_in_from_right);
            viewFlipper.setOutAnimation(this, R.anim.slide_out_to_left);
            viewFlipper.showPrevious();
        }
*/
        return false;
    }

    public void revert() {
        Log.i(TAG,"Reverting");
        // Handling left to right screen swap.
        if (viewFlipper.getDisplayedChild() == 1) {
            viewFlipper.setInAnimation(this, R.anim.slide_in_from_left);
            viewFlipper.setOutAnimation(this, R.anim.slide_out_to_right);
            viewFlipper.showNext();
        }
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        if (firsttime) {
            mSpectrum = new Mat();
            setHsvColor(new Scalar(60,127,127));
            firsttime = false;
        }
        if (lastTouched != -1) {
            if (lastTouched + 5000 < System.currentTimeMillis()) {
                return savedImage;
            }
            lastTouched = -1;
        }
        Mat f = inputFrame.rgba();
        Rect[]  bb = new Rect[512];
     //   Log.i(TAG,"getting the blobs");
        int numblobs = getMediumBlobs(f,bb);
     //   Log.i(TAG,"number of medium blobs is " + numblobs);
        for (int i=0; i< numblobs; i++) {

            Core.rectangle(f, new Point(bb[i].x, bb[i].y), new Point(bb[i].x+bb[i].width, bb[i].y+bb[i].height), new Scalar(255, 0, 0), 5);
        }
        Core.putText(f, Integer.valueOf(numblobs).toString(), new Point(80,80), 1,5.0,new Scalar(0,0,0),4);
        savedImage = f.clone();
   /*     Imgproc.threshold(f, f, 127, 255, Imgproc.THRESH_BINARY);
        FeatureDetector fd = FeatureDetector.create(FeatureDetector.SIMPLEBLOB);
        MatOfKeyPoint kp = new MatOfKeyPoint();
        fd.detect(f,kp);

        for (KeyPoint k : kp.toArray() ) {
            Log.i(TAG, "coordinate " + k.toString());
        }
        Mat g = f.clone();
	    Features2d.drawKeypoints(f, kp, g, new Scalar(255,0,0), Features2d.DRAW_RICH_KEYPOINTS | Features2d.DRAW_OVER_OUTIMG);
        */

        return f;
    }

    public int getMediumBlobs(Mat rgbaImage, Rect[] bb) {
        Mat mPyrDownMat = new Mat();
        Mat mHsvMat = new Mat();
    //    Mat mDilatedMask = new Mat();
        Mat mMask = new Mat();
        Mat mHierarchy = new Mat();
    //    Imgproc.pyrDown(rgbaImage, mPyrDownMat);
     //   Imgproc.pyrDown(mPyrDownMat, mPyrDownMat);

        Imgproc.cvtColor(rgbaImage, mHsvMat, Imgproc.COLOR_RGB2HSV_FULL);

        Core.inRange(mHsvMat, mLowerBound, mUpperBound, mMask);
      //  Imgproc.dilate(mMask, mMask, new Mat());

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();


        Imgproc.findContours(mMask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
//return 20;
      //  Log.i(TAG,"found contours " + contours.size());
        // Find max contour bounding box
        double maxArea = 0;
        Iterator<MatOfPoint> each = contours.iterator();
        int bbi =0;
        while (each.hasNext()) {
            //Log.i(TAG,"looking at a contour");
            MatOfPoint wrapper = each.next();
            double area = Imgproc.contourArea(wrapper);
            if (area > 50 && area < 2000 && bbi < bb.length) {
                bb[bbi++] = Imgproc.boundingRect(wrapper);
            }
        }
        return bbi;

    }
    public void setHsvColor(Scalar hsvColor) {
        double minH = (hsvColor.val[0] >= mColorRadius.val[0]) ? hsvColor.val[0]-mColorRadius.val[0] : 0;
        double maxH = (hsvColor.val[0]+mColorRadius.val[0] <= 255) ? hsvColor.val[0]+mColorRadius.val[0] : 255;

        mLowerBound.val[0] = minH;
        mUpperBound.val[0] = maxH;

        mLowerBound.val[1] = hsvColor.val[1] - mColorRadius.val[1];
        mUpperBound.val[1] = hsvColor.val[1] + mColorRadius.val[1];

        mLowerBound.val[2] = hsvColor.val[2] - mColorRadius.val[2];
        mUpperBound.val[2] = hsvColor.val[2] + mColorRadius.val[2];

        mLowerBound.val[3] = 0;
        mUpperBound.val[3] = 255;

        Mat spectrumHsv = new Mat(1, (int)(maxH-minH), CvType.CV_8UC3);
        for (int j = 0; j < maxH-minH; j++) {
            byte[] tmp = {(byte)(minH+j), (byte)255, (byte)255};
            spectrumHsv.put(0, j, tmp);
        }

        Imgproc.cvtColor(spectrumHsv, mSpectrum, Imgproc.COLOR_HSV2RGB_FULL, 4);
    }
    public Scalar getHSV() {
        return new Scalar(H,S,V);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
                                  boolean fromUser) {

        if (seekBar == HBar) {
            hbTextProgress.setText("The value is: "+progress);
            hbTextAction.setText("changing");
            return;
        }
        if (seekBar == SBar) {
            sbTextProgress.setText("The value is: "+progress);
            sbTextAction.setText("changing");
            return;
        }
        if (seekBar == VBar) {
            vbTextProgress.setText("The value is: "+progress);
            vbTextAction.setText("changing");
        }

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        int value = seekBar.getProgress();
        if (seekBar == HBar) H = value;
        if (seekBar == SBar) S = value;
        if (seekBar == VBar) V = value;
        seekBar.setSecondaryProgress(value);
    }

}
