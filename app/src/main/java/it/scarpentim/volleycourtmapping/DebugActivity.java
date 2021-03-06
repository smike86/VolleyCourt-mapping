package it.scarpentim.volleycourtmapping;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.skydoves.colorpickerpreference.ColorEnvelope;
import com.skydoves.colorpickerpreference.ColorListener;
import com.skydoves.colorpickerpreference.ColorPickerDialog;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.List;

import it.scarpentim.volleycourtmapping.exception.AppException;

public class DebugActivity extends VolleyAbstractActivity {

    VolleySeekBarHandler seekBarHandler;
    private Mat drawedImage;
    private Mat cornersImage;

    TextView tvDebugMsg;
    RadioButton rbLeft;
    RadioButton rbRight;

    private State state = State.START;

    private List<Point> corners = null;


    private enum ShowType{
        IMAGE,
        CANNY,
        HOUGH,
        COLOR_TH,
    }

    private ShowType show = ShowType.IMAGE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);
        seekBarHandler = new VolleySeekBarHandler(this, volleyParams);
        findViewById(R.id.ivPreview).setOnTouchListener(onTouchListener);
        tvDebugMsg = findViewById(R.id.tvDebugMsg);
        rbLeft = findViewById(R.id.radio_left);
        rbRight = findViewById(R.id.radio_right);
        resetRadioButton();
//        createColorPicker();
    }

    private void resetRadioButton() {
        RadioGroup rg = findViewById(R.id.radioGroup);
        rg.clearCheck();
    }

    private void createColorPicker() {
        ColorPickerDialog.Builder builder = new ColorPickerDialog.Builder(this); // (this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("ColorPicker Dialog");
        builder.setPreferenceName("MyColorPickerDialog");
//        builder.setFlagView(new CustomFlag(this, R.layout.layout_flag));
        builder.setPositiveButton(getString(R.string.confirm), new ColorListener() {

            @Override
            public void onColorSelected(ColorEnvelope colorEnvelope) {
                toast("colore: #" + colorEnvelope.getColorHtml());
//                TextView textView = findViewById(R.id.textView);
//                textView.setText("#" + colorEnvelope.getColorHtml());
//
//                LinearLayout linearLayout = findViewById(R.id.linearLayout);
//                linearLayout.setBackgroundColor(colorEnvelope.getColor());
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.debugmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (state.isProcessing())
            return false;

        int id = item.getItemId();
        switch (id){
            case R.id.action_goBack:
                finish();
                break;
            case R.id.action_openGallery:
                state = State.START;
                show = ShowType.IMAGE;
                PermissionSupport.validateReadStoragePermission(this);
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_PICK);
                startActivityForResult(Intent.createChooser(intent,"Select image"), SELECT_PICTURE);
                break;

            case R.id.action_Canny:
                if (checkImageLoaded()){
                    show = ShowType.CANNY;
                    showImage();
                    state = State.IMAGE_PROCESSING;
                }
                break;

            case R.id.action_Hough:
                if (checkImageLoaded()){
                    show = ShowType.HOUGH;
                    showImage();
                    state = State.IMAGE_PROCESSING;
                }
                break;

            case R.id.action_Color_slicing:
                if (checkImageLoaded()){
                    show = ShowType.COLOR_TH;
                    showImage();
                    state = State.IMAGE_PROCESSING;
                }
                break;

            case R.id.action_resetImage:
                if (checkImageLoaded()){
                    show = ShowType.IMAGE;
                    showImage();
                    state = State.IMAGE_PROCESSING;
                }
                break;
        }

        setViewState();
        return super.onOptionsItemSelected(item);

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                selectedImagePath = imageSupport.getPath(selectedImageUri);
                volleyParams.setLastImage(selectedImagePath);
                loadImageFromPath();
            }
        }
    }

    @Override
    protected boolean loadImageFromPath() {
        boolean loaded = super.loadImageFromPath();
        if(loaded){
            state = State.IMAGE_LOADED;
            onTouchListener.setImage(sampledImage);
            setViewState();
        }
        return loaded;
    }

    @Override
    public void showImage() {
        if (!checkImageLoaded()) return;
        Bitmap bitmap;
        Mat image;

        switch (show) {
            case IMAGE:
                bitmap = imageSupport.matToBitmap(sampledImage);
                break;
            case COLOR_TH:
                image = flipIfNeeded(sampledImage);
                Mat maskedSMat = imageSupport.colorMask(image);
                bitmap = imageSupport.matToBitmap(flipIfNeeded(maskedSMat));
                break;

            case CANNY:
                image = flipIfNeeded(sampledImage);
                Mat maskedCMat = imageSupport.colorMask(image);
                Mat cannyMat = imageSupport.edgeDetector(maskedCMat, volleyParams);
                bitmap = imageSupport.matToBitmap(flipIfNeeded(cannyMat));
                break;
            case HOUGH:
                image = flipIfNeeded(sampledImage);
                Mat maskedMat = imageSupport.colorMask(image);
                Mat houghMat = imageSupport.houghTransform(maskedMat, volleyParams);
                drawedImage = imageSupport.drawHoughLines(maskedMat, houghMat);
                bitmap = imageSupport.matToBitmap(flipIfNeeded(drawedImage));

//                Mat houghMat = imageSupport.houghTransformWithColorFilter(sampledImage, seekBarHandler);
//                drawedImage = imageSupport.drawHoughLines(sampledImage, houghMat);
//                bitmap = imageSupport.matToBitmap(drawedImage);

                break;
            default:
                return;
        }

        ImageView iv = findViewById(R.id.ivPreview);
        iv.setImageBitmap(bitmap);
    }

    private Mat flipIfNeeded(Mat sampledImage) {
        Mat image;
        if (isLeft())
            image = imageSupport.flip(sampledImage);
        else
            image = sampledImage;
        return image;
    }


    public void showImage(Mat image) {
        if (!checkImageLoaded()) return;
        Bitmap bitmap;
        bitmap = imageSupport.matToBitmap(image);
        ImageView iv = findViewById(R.id.ivPreview);
        iv.setImageBitmap(bitmap);
    }

    private boolean checkImageLoaded() {
        if (sampledImage == null){
            Toast.makeText(this, getString(R.string.no_image_uploaded), Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV loaded successfully");
                    loadImageFromPath();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    @Override
    protected void showMessage(int stringId) {
        tvDebugMsg.setText(stringId);
    }

//    public void manualTransformImage(View view) {
//        if (isPerspectiveApplied) {
//            isPerspectiveApplied = false;
//            onTouchListener.reset();
//            onTouchListener.setImage(sampledImage);
//            showImage(sampledImage);
//        } else {
//            if (sampledImage == null) {
//                toast(getString(R.string.no_image_uploaded));
//                return;
//            }
//            if (onTouchListener.getCorners().size() != 4) {
//                toast("You must select 4 vertices!");
//            } else {
//                isPerspectiveApplied = true;
//
//                Mat correctedImage = imageSupport.projectOnHalfCourt(onTouchListener.getCorners(), sampledImage);
//                showImage(correctedImage);
//            }
//        }
//    }

    public void findDebugCorners(View view) {
        if (state.isProcessing())
            return;
        //resetPerspectiveApplied();
        //resetClassificationMsg();
        state = State.CORNERS;
        if (sampledImage == null) {
            Toast.makeText(this, R.string.no_image_uploaded, Toast.LENGTH_SHORT).show();
            return;
        }
        onTouchListener.reset();
        Mat image = flipIfNeeded(sampledImage);
        Mat maskedMat = imageSupport.colorMask(image);
        Mat houghMat = imageSupport.houghTransform(maskedMat, volleyParams);


        List<Point> corners = null;
        try {
            corners = imageSupport.findCourtExtremesFromRigthView(houghMat);
        } catch (AppException e) {
            toast(e.getLocalizedMessage() + ".\n" + getString(R.string.try_manually));
            onTouchListener.enable();
            state = State.CORNERS_SELECTION;
            setViewState();
            return;
        }

        Mat drawedMat =  image.clone();
        for (Point corner : corners) {
            Imgproc.circle(drawedMat, corner, (int) 20, new Scalar(0,100,255),3);
        }
        cornersImage = flipIfNeeded(drawedMat);
        showImage(cornersImage);

        this.corners = corners;
        setViewState();
        //Mat correctedImage = imageSupport.projectOnHalfCourt(onTouchListener.getCorners(), sampledImage);
        //showImage(correctedImage);
    }

    @Override
    public void onFinishManuallyCornersSelection(List<Point> corners){
        cornersImage = onTouchListener.getImage();
        this.corners = flipCornersIfNeeded(corners);
    }

    public void transformImage(View view) {
        if (state.isProcessing())
            return;

        if (state == State.TRANSFORM) {
            state = State.CORNERS;
            showImage(sampledImage);
        }else if (corners == null || corners.size() != 4) {
            toast(R.string.corners_before);
        } else {
            state = State.TRANSFORM;
            ((TextView) view).setText(R.string.goback);
            Mat image = flipIfNeeded(sampledImage);
            Mat correctedImage = imageSupport.projectOnHalfCourt(corners, image);
            correctedImage = flipIfNeeded(correctedImage);
            showImage(correctedImage);
        }
        setViewState();
    }

    private List<Point> flipCornersIfNeeded(List<Point> corners) {
        if(isLeft())
            return imageSupport.flipCorners(corners);
        else
            return corners;
    }

    public void classifyImage(View view){
        if (state.isProcessing())
            return;

        if (sampledImage == null) {
            Toast.makeText(this, getString(R.string.no_image_uploaded), Toast.LENGTH_SHORT).show();
            return;
        }
        state = State.CLASSIFY_START;
        setViewState();
        super.executeAsyncClassification(sampledImage, isLeft());
    }

    @Override
    protected void onPostExecuteClassifierTask() {
        state = State.CLASSIFY_END;
        setViewState();
    }

    public void findPositions(View view) {
        if (state.isProcessing())
            return;

        if(state == State.POSITIONS_START){
            return;
        }else if (state == State.POSITIONS_END_OK){
            state = State.CORNERS;
            show = ShowType.IMAGE;
            showImage();
        }else if (corners == null || corners.size() != 4) {
            toast(R.string.corners_before);
        } else {
            state = State.POSITIONS_START;
            DigitalizationTask computeTask = new DigitalizationTask(isLeft(), corners, false);
            computeTask.execute();
        }
        setViewState();

    }

    @Override
    protected void onPostExecuteDigitalizationTask(boolean success) {
        super.onPostExecuteDigitalizationTask(success);
        if (success)
            state = State.POSITIONS_END_OK;
        else
            state = State.POSITIONS_END_ERR;
        setViewState();
    }

    private boolean isLeft() {
        return rbLeft.isChecked();
    }

    private void setViewState(){

        if (sampledImage == null)
            return;

        Button btnTransform = findViewById(R.id.btn_transform);
        Button btnPositions = findViewById(R.id.btn_positions);
        Button btnClassify = findViewById(R.id.btn_classify);
        Button btnCorners = findViewById(R.id.btn_corners);

        TextView msgView = findViewById(R.id.tvDebugMsg);

        switch (state){
            case START :
            case IMAGE_LOADED:
                btnTransform.setText(R.string.transform);
                btnPositions.setText(R.string.positions);
                showConfigLayout(View.VISIBLE);
                msgView.setText(R.string.empty);
                onTouchListener.reset();
                corners = null;
                btnTransform.setEnabled(false);
                btnPositions.setEnabled(false);
                btnClassify.setEnabled(false);
                btnCorners.setEnabled(false);
                resetRadioButton();
                break;
            case IMAGE_PROCESSING:
                btnTransform.setText(R.string.transform);
                btnPositions.setText(R.string.positions);
                showConfigLayout(View.VISIBLE);
                msgView.setText(R.string.empty);
                onTouchListener.reset();
                corners = null;
                break;
            case SIDE_SELECTED:
                btnTransform.setEnabled(true);
                btnPositions.setEnabled(true);
                btnClassify.setEnabled(true);
                btnCorners.setEnabled(true);
                btnTransform.setText(R.string.transform);
                btnPositions.setText(R.string.positions);
                showConfigLayout(View.VISIBLE);
                msgView.setText(R.string.empty);
                onTouchListener.reset();
                corners = null;
                break;
            case CORNERS:
                btnTransform.setText(R.string.transform);
                btnPositions.setText(R.string.positions);
                showConfigLayout(View.VISIBLE);
                msgView.setText(R.string.empty);
                showImage(cornersImage);
                break;
            case CORNERS_SELECTION:
                show = ShowType.IMAGE;
                showImage();
                btnTransform.setText(R.string.transform);
                btnPositions.setText(R.string.positions);
                showConfigLayout(View.VISIBLE);
                msgView.setText(R.string.select_4_corners);
                break;
            case TRANSFORM:
                btnTransform.setText(R.string.goback);
                btnPositions.setText(R.string.positions);
                showConfigLayout(View.GONE);
                msgView.setText(R.string.empty);
                break;
            case POSITIONS_START:
                msgView.setText(R.string.processing);
                btnTransform.setText(R.string.transform);
                btnPositions.setText(R.string.goback);
                break;
            case POSITIONS_END_OK:
                btnTransform.setText(R.string.transform);
                btnPositions.setText(R.string.goback);
                showConfigLayout(View.GONE);
                break;
            case POSITIONS_END_ERR:
                btnTransform.setText(R.string.transform);
                btnPositions.setText(R.string.positions);
                break;
            case CLASSIFY_START:
                btnTransform.setText(R.string.transform);
                btnPositions.setText(R.string.positions);
                break;
            case CLASSIFY_END:
                btnTransform.setText(R.string.transform);
                btnPositions.setText(R.string.positions);
                showConfigLayout(View.VISIBLE);
                break;
        }

    }

    public void radioChange(View view) {
        state = State.SIDE_SELECTED;
        setViewState();
    }

    private void showConfigLayout(int visible) {
        ConstraintLayout configLayout = findViewById(R.id.layout_config);
        configLayout.setVisibility(visible);
    }

//    private void resetPerspectiveApplied() {
//        Button b = findViewById(R.id.btn_positions);
//        b.setText(R.string.positions);
//
//        if (isPerspectiveApplied) {
//            isPerspectiveApplied = false;
//            Button btn = findViewById(R.id.btn_transform);
//            btn.setText(R.string.transform);
//            showConfigLayout(View.VISIBLE);
//        }
//    }
//    private void resetClassificationMsg() {
//        TextView msgView = findViewById(R.id.tvDebugMsg);
//        msgView.setText(R.string.empty);
//    }

    private enum State {
        START,
        SIDE_SELECTED,
        IMAGE_LOADED,
        CORNERS,
        CORNERS_SELECTION,
        TRANSFORM,
        CLASSIFY_START(true),
        CLASSIFY_END,
        POSITIONS_START(true),
        POSITIONS_END_OK,
        IMAGE_PROCESSING, POSITIONS_END_ERR;

        private boolean processing;
        State() {
            this.processing = false;
        }
        State(boolean processing) {
            this.processing = processing;
        }

        public boolean isProcessing() {
            return processing;
        }
    }


}
