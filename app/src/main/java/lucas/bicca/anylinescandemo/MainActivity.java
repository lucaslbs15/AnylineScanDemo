package lucas.bicca.anylinescandemo;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ProgressDialog;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;

import at.nineyards.anyline.camera.CameraController;
import at.nineyards.anyline.camera.CameraOpenListener;
import at.nineyards.anyline.models.AnylineImage;
import at.nineyards.anyline.modules.document.DocumentResult;
import at.nineyards.anyline.modules.document.DocumentResultListener;
import at.nineyards.anyline.modules.document.DocumentScanView;
import lucas.bicca.anylinescandemo.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements CameraOpenListener {

    private ActivityMainBinding binding;
    private Toast notificationToast;
    private List<PointF> lastOutline;
    private ProgressDialog progressDialog;
    private ObjectAnimator errorMessageAnimator;
    private long lastErrorRecieved = 0;

    private android.os.Handler handler = new android.os.Handler();

    private Runnable errorMessageCleanup = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() > lastErrorRecieved + getApplication().getResources().getInteger(R.integer.error_message_delay)) {
                if (binding.activityErrorMessage == null || errorMessageAnimator == null) {
                    return;
                }
                if (binding.activityErrorMessage.getAlpha() == 0f) {
                    binding.activityErrorMessage.setText("");
                } else if (!errorMessageAnimator.isRunning()) {
                    errorMessageAnimator = ObjectAnimator.ofFloat(binding.activityErrorMessage, "alpha", binding.activityErrorMessage.getAlpha(), 0f);
                    errorMessageAnimator.setDuration(getResources().getInteger(R.integer.error_message_delay));
                    errorMessageAnimator.setInterpolator(new AccelerateInterpolator());
                    errorMessageAnimator.start();
                }
            }
            handler.postDelayed(errorMessageCleanup, getResources().getInteger(R.integer.error_message_delay));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        binding.activityMainDocumentScanView.setCameraOpenListener(this);
        binding.activityMainDocumentScanView.setDocumentRatios(DocumentScanView.DocumentRatio.DIN_AX_PORTRAIT.getRatio(), DocumentScanView.DocumentRatio.DIN_AX_LANDSCAPE.getRatio());
        binding.activityMainDocumentScanView.setMaxDocumentRatioDeviation(0.15);
        initAnyline();
        initRestartClick();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("Bicca", "onResume called");
        binding.activityMainDocumentScanView.startScanning();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i("Bicca", "onPause called");
        binding.activityMainDocumentScanView.cancelScanning();
        binding.activityMainDocumentScanView.releaseCameraInBackground();
    }

    private void initRestartClick() {
        binding.activityRestartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                binding.activityMainDocumentScanView.startScanning();
                binding.activityImageViewResult.setImageBitmap(null);
            }
        });
    }

    private void initAnyline() {
        binding.activityMainDocumentScanView.initAnyline(getString(R.string.anyline_license_key),
                new DocumentResultListener() {

                    @Override
                    public void onPreviewProcessingSuccess(AnylineImage anylineImage) {
                        Log.i("Bicca", "onPreviewProcessingSuccess called");
                    }

                    @Override
                    public void onPreviewProcessingFailure(DocumentScanView.DocumentError documentError) {
                        Log.i("Bicca", "onPreviewProcessingFailure called");
                        showErrorMessageFor(documentError);
                    }

                    @Override
                    public void onPictureProcessingFailure(DocumentScanView.DocumentError documentError) {
                        Log.i("Bicca", "onPictureProcessingFailure called");
                        showErrorMessageFor(documentError, true);
                        closeProgressDialog();

                        AnylineImage image = binding.activityMainDocumentScanView.getCurrentFullImage();

                        if (image != null) {
                            File outDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "error");
                            outDir.mkdir();
                            File outFile = new File(outDir, "" + System.currentTimeMillis() + documentError.name() + ".jpg");
                            try {
                                image.save(outFile, 100);
                                Log.i("Bicca", "onPictureProcessingFailure - error image saved to " + outFile.getAbsolutePath());
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                            image.release();
                        }
                    }

                    @Override
                    public boolean onDocumentOutlineDetected(List<PointF> list, boolean b) {
                        Log.i("Bicca", "onDocumentOutlineDetected called");
                        lastOutline = list;
                        return false;
                    }

                    @Override
                    public void onTakePictureSuccess() {
                        if (progressDialog == null) {
                            progressDialog = ProgressDialog.show(MainActivity.this, "Atenção", "Processando a captura de documento", true);
                        }

                        if (errorMessageAnimator != null && errorMessageAnimator.isRunning()) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    errorMessageAnimator.cancel();
                                    binding.activityErrorMessageLayout.setVisibility(View.GONE);
                                }
                            });
                        }
                        Log.i("Bicca", "onTakePictureSuccess called");
                    }

                    @Override
                    public void onTakePictureError(Throwable throwable) {
                        Log.i("Bicca", "onTakePictureError called");
                    }

                    @Override
                    public void onPictureCornersDetected(AnylineImage anylineImage, List<PointF> list) {
                        Log.i("Bicca", "onPictureCornersDetected called");
                    }

                    @Override
                    public void onPictureTransformed(AnylineImage anylineImage) {
                        Log.i("Bicca", "onPictureTransformed called");
                    }

                    @Override
                    public void onPictureTransformError(DocumentScanView.DocumentError documentError) {
                        Log.i("Bicca", "onPictureTransformError called");
                    }

                    @Override
                    public void onResult(DocumentResult documentResult) {
                        Log.i("Bicca", "onResult called");
                        AnylineImage transformedImage = documentResult.getResult();
                        AnylineImage fullFramge = documentResult.getFullImage();

                        closeProgressDialog();

                        int widthDP;
                        int heightDP;
                        Bitmap bmpTransformedImage = transformedImage.getBitmap();

                        if (bmpTransformedImage.getHeight() > bmpTransformedImage.getWidth()) {
                            widthDP = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100, getResources().getDisplayMetrics());
                            heightDP = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 160, getResources().getDisplayMetrics());
                            binding.activityImageViewResult.getLayoutParams().width = widthDP;
                            binding.activityImageViewResult.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        } else {
                            widthDP = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 160, getResources().getDisplayMetrics());
                            heightDP = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100, getResources().getDisplayMetrics());
                            binding.activityImageViewResult.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                            binding.activityImageViewResult.getLayoutParams().height = heightDP;
                        }

                        binding.activityImageViewResult.setImageBitmap(Bitmap.createScaledBitmap(transformedImage.getBitmap(), widthDP, heightDP, false));

                        File outDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ok");
                        outDir.mkdir();
                        File outFile = new File(outDir, "" + System.currentTimeMillis() + ".jpg");
                        try {
                            transformedImage.save(outFile, 100);
                            showToast("Imagem salva para " + outFile.getAbsolutePath());
                        } catch (IOException ex) {
                            Log.e("Bicca", "onResult exception: " + ex.getMessage());
                            ex.printStackTrace();
                        }

                        transformedImage.release();
                        fullFramge.release();
                    }
                });
    }

    @Override
    public void onCameraOpened(CameraController cameraController, int i, int i1) {
        Log.i("Bicca", "onCameraOpened called");
    }

    @Override
    public void onCameraError(Exception e) {
        throw new RuntimeException(e);
    }

    private void showToast(String text) {
        try {
            notificationToast.setText(text);
        } catch (Exception e) {
            notificationToast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        }
        notificationToast.show();
    }

    private void showErrorMessageFor(DocumentScanView.DocumentError documentError) {
        showErrorMessageFor(documentError, false);
    }

    private void showErrorMessageFor(DocumentScanView.DocumentError documentError, boolean highlight) {
        String text = "Erro na captura da imagem: ";
        switch (documentError) {
            case DOCUMENT_NOT_SHARP:

                break;
            case DOCUMENT_SKEW_TOO_HIGH:

                break;
            case DOCUMENT_OUTLINE_NOT_FOUND:
                text += "documento não encontrado";
                break;
            case IMAGE_TOO_DARK:
                text += "imagem muito escura";
                break;
            case SHAKE_DETECTED:
                text += "tremida detectada";
                break;
            case DOCUMENT_BOUNDS_OUTSIDE_OF_TOLERANCE:
                text += "limites estão fora do tolerado";
                break;
            case DOCUMENT_RATIO_OUTSIDE_OF_TOLERANCE:
                text += "proporção fora do tolerado";
                break;
            case UNKNOWN:
                break;
            default:
                text += "erro desconhecido";

        }

        if (highlight) {
            showHighlightErrorMessageUiAnimated(text);
        } else {
            showErrorMessageUiAnimated(text);
        }
    }

    private void closeProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
    }

    private void showErrorMessageUiAnimated(String message) {
        if (lastErrorRecieved == 0) {
            handler.post(errorMessageCleanup);
        }
        lastErrorRecieved = System.currentTimeMillis();
        if (errorMessageAnimator != null && (errorMessageAnimator.isRunning() || binding.activityErrorMessage.getText().equals(message))) {
            return;
        }

        binding.activityErrorMessageLayout.setVisibility(View.VISIBLE);
        binding.activityErrorMessage.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
        binding.activityErrorMessage.setAlpha(0f);
        binding.activityErrorMessage.setText(message);
        errorMessageAnimator = ObjectAnimator.ofFloat(binding.activityErrorMessage, "alpha", 0f, 1f);
        errorMessageAnimator.setDuration(getResources().getInteger(R.integer.error_message_delay));
        errorMessageAnimator.setInterpolator(new DecelerateInterpolator());
        errorMessageAnimator.start();
    }

    private void showHighlightErrorMessageUiAnimated(String message) {
        lastErrorRecieved = System.currentTimeMillis();
        binding.activityErrorMessageLayout.setVisibility(View.VISIBLE);
        binding.activityErrorMessage.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        binding.activityErrorMessage.setAlpha(0f);
        binding.activityErrorMessage.setText(message);

        if (errorMessageAnimator != null && errorMessageAnimator.isRunning()) {
            errorMessageAnimator.cancel();
        }

        errorMessageAnimator = ObjectAnimator.ofFloat(binding.activityErrorMessage, "alpha", 0f, 1f);
        errorMessageAnimator.setDuration(getResources().getInteger(R.integer.error_message_delay));
        errorMessageAnimator.setInterpolator(new DecelerateInterpolator());
        errorMessageAnimator.setRepeatMode(ValueAnimator.REVERSE);
        errorMessageAnimator.setRepeatCount(1);
        errorMessageAnimator.start();
    }
}
