package io.github.gretchycat.infinemesh.android;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import io.github.gretchycat.infinemesh.ModelDocument;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

/** Reusable viewer surface. Host owns loading, lifecycle and playback clock. */
public final class MeshView extends GLSurfaceView {
    public enum Shading { WIREFRAME, FLAT, GOURAUD }
    private final MeshRenderer renderer=new MeshRenderer();
    private final ScaleGestureDetector scale;
    private float lastX,lastY;
    private boolean multiTouch;
    public MeshView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(MeshView::chooseDepthConfig);
        setRenderer(renderer); setRenderMode(RENDERMODE_WHEN_DIRTY);
        scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float factor=detector.getScaleFactor(); change(()->renderer.zoom(factor)); return true;
            }
        });
        setContentDescription("3D viewer. Drag to rotate, pinch to zoom. Reset buttons are above the view.");
    }
    private static EGLConfig chooseDepthConfig(EGL10 egl,EGLDisplay display) {
        // Request 24-bit depth first; retain 16-bit devices with the tightly fitted frustum.
        for(int depth:new int[]{24,16}) {
            int[] attributes={EGL10.EGL_RED_SIZE,8,EGL10.EGL_GREEN_SIZE,8,EGL10.EGL_BLUE_SIZE,8,
                EGL10.EGL_ALPHA_SIZE,8,EGL10.EGL_DEPTH_SIZE,depth,
                EGL10.EGL_SURFACE_TYPE,EGL10.EGL_WINDOW_BIT,0x3040,4,EGL10.EGL_NONE};
            int[] count=new int[1];
            if(!egl.eglChooseConfig(display,attributes,null,0,count) || count[0]==0) continue;
            EGLConfig[] configs=new EGLConfig[count[0]];
            if(!egl.eglChooseConfig(display,attributes,configs,configs.length,count)) continue;
            EGLConfig best=configs[0];int bestScore=Integer.MAX_VALUE;
            for(EGLConfig config:configs) {
                int[] value=new int[1];
                egl.eglGetConfigAttrib(display,config,EGL10.EGL_DEPTH_SIZE,value);
                int score=Math.abs(value[0]-depth);
                egl.eglGetConfigAttrib(display,config,EGL10.EGL_SAMPLES,value);
                score+=value[0]*32;
                if(score<bestScore) { best=config;bestScore=score; }
            }
            return best;
        }
        throw new IllegalStateException("No GLES 2.0 display configuration with a depth buffer");
    }
    /** Call on a worker: buffer and normal preparation can be expensive. Safe after pause. */
    public void setDocument(ModelDocument document) {
        MeshRenderer.Scene scene=MeshRenderer.Scene.prepare(document);
        change(()->renderer.setScene(scene));
    }
    public void setShading(Shading shading) { change(()->renderer.shading=shading); }
    public void setColors(int background,int model) { change(()->{ renderer.background=background; renderer.color=model; }); }
    public void setGridVisible(boolean visible) { change(()->renderer.gridVisible=visible); }
    public void setTravelVisible(boolean visible) { change(()->renderer.travelVisible=visible); }
    public void setPreviewSeconds(double seconds) { change(()->renderer.seconds=seconds); }
    public void resetRotation() { change(renderer::resetRotation); }
    public void resetZoom() { change(()->renderer.zoom=1); }
    public void zoomBy(float factor) { change(()->renderer.zoom(factor)); }
    private void change(Runnable action) { queueEvent(action); requestRender(); }
    @Override public boolean onTouchEvent(MotionEvent e) {
        scale.onTouchEvent(e);
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) { lastX=e.getX(); lastY=e.getY(); multiTouch=false; return true; }
        if(e.getPointerCount()>1) multiTouch=true;
        if(action==MotionEvent.ACTION_MOVE) {
            if(!multiTouch && !scale.isInProgress()) {
                float dx=(e.getX()-lastX)*0.5f, dy=(e.getY()-lastY)*0.5f;
                change(()->renderer.rotate(dx,dy));
            }
            lastX=e.getX(); lastY=e.getY();
        }
        if(action==MotionEvent.ACTION_POINTER_UP) {
            int remaining=e.getActionIndex()==0?1:0;
            lastX=e.getX(remaining); lastY=e.getY(remaining);
            // Wait for the next single-finger down to prevent a pinch-to-rotation jump.
        }
        if(action==MotionEvent.ACTION_UP) performClick();
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
