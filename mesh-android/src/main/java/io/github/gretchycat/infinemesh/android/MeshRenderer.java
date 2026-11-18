package io.github.gretchycat.infinemesh.android;

import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import java.nio.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import io.github.gretchycat.infinemesh.*;

final class MeshRenderer implements GLSurfaceView.Renderer {
    static final class Scene {
        final ModelDocument document;
        final FloatBuffer vertices,flat,smooth,edges,grid;
        final float[] normalized;
        final boolean[] extrusion;
        final double[] ends;
        final double scale;
        Scene(ModelDocument doc,FloatBuffer vertices,FloatBuffer flat,FloatBuffer smooth,FloatBuffer edges,FloatBuffer grid,float[] normalized,boolean[] extrusion,double[] ends,double scale) {
            this.document=doc;this.vertices=vertices;this.flat=flat;this.smooth=smooth;this.edges=edges;this.grid=grid;this.normalized=normalized;this.extrusion=extrusion;this.ends=ends;this.scale=scale;
        }
        static Scene prepare(ModelDocument doc) {
            Bounds b=doc.bounds(); double scale=2/Math.max(b.extent(),1e-12);
            float[] p=doc.mesh!=null?doc.mesh.positions():doc.toolpath.positions();
            for(int i=0;i<p.length;i++) p[i]=(float)(((double)p[i]-b.center(i%3))*scale);
            FloatBuffer vertices=buffer(p), flat=null,smooth=null,edges=null;
            if(doc.mesh!=null) {
                flat=buffer(doc.mesh.normals(false)); smooth=buffer(doc.mesh.normals(true));
                edges=allocate(p.length*2);
                for(int i=0;i<p.length;i+=9) for(int edge=0;edge<3;edge++) {
                    edges.put(p,i+edge*3,3); edges.put(p,i+((edge+1)%3)*3,3);
                }
                edges.position(0);
            }
            float z=(float)(((double)b.minZ()-b.center(2))*scale)-0.015f;
            FloatBuffer grid=allocate(21*12);
            for(int i=-10;i<=10;i++) { float v=i*0.15f; grid.put(new float[]{v,-1.5f,z,v,1.5f,z,-1.5f,v,z,1.5f,v,z}); }
            grid.position(0);
            return new Scene(doc,vertices,flat,smooth,edges,grid,p,doc.toolpath==null?null:doc.toolpath.extrusion(),doc.toolpath==null?null:doc.toolpath.endSeconds(),scale);
        }
    }
    private static FloatBuffer allocate(int count) { return ByteBuffer.allocateDirect(count*4).order(ByteOrder.nativeOrder()).asFloatBuffer(); }
    private static FloatBuffer buffer(float[] values) { FloatBuffer out=allocate(values.length);out.put(values).position(0);return out; }
    private Scene scene;
    private int program,position,normal,mvp,model,lit,tint;
    private int width=1,height=1;
    private final float[] projection=new float[16],view=new float[16],transform=new float[16],mv=new float[16],combined=new float[16];
    private final FloatBuffer dynamic=allocate(6);
    private final FloatBuffer emptyGrid=buffer(new float[]{-1,0,0,1,0,0,0,-1,0,0,1,0});
    MeshView.Shading shading=MeshView.Shading.GOURAUD;
    int background=0xff17212b,color=0xff65c9bd;
    boolean gridVisible=true,travelVisible=true;
    double seconds=Double.POSITIVE_INFINITY;
    float zoom=1,yaw=-35,pitch=-60;
    void zoom(float factor) { zoom=Math.max(0.1f,Math.min(15,zoom*factor)); }
    void rotate(float x,float y) { yaw=(yaw+x)%360; pitch=(pitch+y)%360; }
    void resetRotation() { yaw=-35;pitch=-60; }
    void setScene(Scene s) { scene=s;zoom=1;resetRotation();seconds=Double.POSITIVE_INFINITY; }
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
        String vertex="uniform mat4 uMvp; uniform mat4 uModel; attribute vec3 aPosition; attribute vec3 aNormal; uniform float uLit; varying float vLight; void main(){ gl_Position=uMvp*vec4(aPosition,1.0); gl_PointSize=10.0; vec3 n=normalize(mat3(uModel)*aNormal); vLight=mix(1.0,0.25+0.75*abs(dot(n,normalize(vec3(-0.4,0.7,1.0)))),uLit); }";
        String fragment="precision mediump float; uniform vec4 uColor; varying float vLight; void main(){ gl_FragColor=vec4(uColor.rgb*vLight,uColor.a); }";
        program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,vertex)); GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,fragment)); GLES20.glLinkProgram(program);
        int[] status=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0);
        if(status[0]==0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        position=GLES20.glGetAttribLocation(program,"aPosition");normal=GLES20.glGetAttribLocation(program,"aNormal");
        mvp=GLES20.glGetUniformLocation(program,"uMvp");model=GLES20.glGetUniformLocation(program,"uModel");lit=GLES20.glGetUniformLocation(program,"uLit");tint=GLES20.glGetUniformLocation(program,"uColor");
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }
    private static int shader(int type,String source) {
        int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);
        int[] status=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,status,0);
        if(status[0]==0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(s)); return s;
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h) { width=Math.max(1,w);height=Math.max(1,h);GLES20.glViewport(0,0,w,h); }
    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(Color.red(background)/255f,Color.green(background)/255f,Color.blue(background)/255f,1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program);
        float aspect=(float)width/height;
        float distance=4.8f/Math.min(1,aspect)/zoom;
        Matrix.perspectiveM(projection,0,45,aspect,Math.max(0.001f,distance/1000),distance+10);
        Matrix.setLookAtM(view,0,0,0,distance,0,0,0,0,1,0);
        Matrix.setIdentityM(transform,0);Matrix.rotateM(transform,0,pitch,1,0,0);Matrix.rotateM(transform,0,yaw,0,0,1);
        Matrix.multiplyMM(mv,0,view,0,transform,0);Matrix.multiplyMM(combined,0,projection,0,mv,0);
        GLES20.glUniformMatrix4fv(mvp,1,false,combined,0);GLES20.glUniformMatrix4fv(model,1,false,transform,0);
        if(gridVisible) draw(scene==null?emptyGrid:scene.grid,null,GLES20.GL_LINES,0,scene==null?4:84,gridColor(),1);
        if(scene==null) return;
        if(scene.document.mesh!=null) {
            if(shading==MeshView.Shading.WIREFRAME) draw(scene.edges,null,GLES20.GL_LINES,0,scene.edges.capacity()/3,color,1);
            else draw(scene.vertices,shading==MeshView.Shading.FLAT?scene.flat:scene.smooth,GLES20.GL_TRIANGLES,0,scene.vertices.capacity()/3,color,1);
        } else drawToolpath();
    }
    private int gridColor() {
        int brightness=(Color.red(background)+Color.green(background)+Color.blue(background))/3;
        return brightness>140?0xff87949a:0xff465968;
    }
    private void drawToolpath() {
        Toolpath path=scene.document.toolpath;
        double t=Math.max(0,Math.min(seconds,path.durationSeconds()));
        int completed=path.completedSegments(t),run=0;
        // Batch contiguous travel/extrusion runs; no per-segment GL calls for normal slicer output.
        while(run<completed) {
            boolean e=scene.extrusion[run];int end=run+1;
            while(end<completed && scene.extrusion[end]==e) end++;
            if(e || travelVisible) draw(scene.vertices,null,GLES20.GL_LINES,run*2,(end-run)*2,e?color:gridColor(),1);
            run=end;
        }
        float[] sourceHead=path.headAt(t),head=new float[3];Bounds b=path.bounds();
        for(int a=0;a<3;a++) head[a]=(float)(((double)sourceHead[a]-b.center(a))*scene.scale);
        if(completed<path.segmentCount() && (scene.extrusion[completed] || travelVisible)) {
            dynamic.position(0);dynamic.put(scene.normalized,completed*6,3);dynamic.put(head).position(0);
            draw(dynamic,null,GLES20.GL_LINES,0,2,scene.extrusion[completed]?color:gridColor(),1);
        }
        dynamic.position(0);dynamic.put(head).position(0);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);draw(dynamic,null,GLES20.GL_POINTS,0,1,0xffffba66,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }
    private void draw(FloatBuffer vertices,FloatBuffer normals,int mode,int first,int count,int color,float alpha) {
        vertices.position(0);GLES20.glEnableVertexAttribArray(position);GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,0,vertices);
        if(normals!=null) { normals.position(0);GLES20.glEnableVertexAttribArray(normal);GLES20.glVertexAttribPointer(normal,3,GLES20.GL_FLOAT,false,0,normals); }
        else { GLES20.glDisableVertexAttribArray(normal);GLES20.glVertexAttrib3f(normal,0,0,1); }
        GLES20.glUniform1f(lit,normals==null?0:1);
        GLES20.glUniform4f(tint,Color.red(color)/255f,Color.green(color)/255f,Color.blue(color)/255f,alpha);
        GLES20.glDrawArrays(mode,first,count);
    }
}
