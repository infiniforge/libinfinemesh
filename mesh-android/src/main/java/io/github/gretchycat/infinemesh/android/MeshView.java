package io.github.gretchycat.infinemesh.android;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import io.github.gretchycat.infinemesh.ModelDocument;

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
        setEGLConfigChooser(8,8,8,8,16,0);
        setRenderer(renderer); setRenderMode(RENDERMODE_WHEN_DIRTY);
        scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float factor=detector.getScaleFactor(); change(()->renderer.zoom(factor)); return true;
            }
        });
        setContentDescription("3D viewer. Drag to rotate, pinch to zoom. Reset buttons are above the view.");
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
