/* Optional host GLES test: a nearer red face must cover a blue face 0.0001 units behind it. */
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <stdio.h>
#include <stdlib.h>
#include "viewer-shaders.h"

static GLuint shader(GLenum type,const char *source) {
    GLuint id=glCreateShader(type);glShaderSource(id,1,&source,NULL);glCompileShader(id);
    GLint ok;glGetShaderiv(id,GL_COMPILE_STATUS,&ok);
    if(!ok) { char log[4096];glGetShaderInfoLog(id,sizeof(log),NULL,log);fprintf(stderr,"%s\n",log);exit(1); }
    return id;
}
static int render(EGLDisplay display,int bits,float near,float far) {
    EGLint attributes[]={EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_NONE};
    EGLConfig configs[128],config=NULL;EGLint count=0;
    if(!eglChooseConfig(display,attributes,configs,128,&count)) exit(1);
    for(int i=0;i<count;i++) {
        EGLint depth,samples;eglGetConfigAttrib(display,configs[i],EGL_DEPTH_SIZE,&depth);
        eglGetConfigAttrib(display,configs[i],EGL_SAMPLES,&samples);
        if(samples==0) { config=configs[i];break; }
    }
    if(!config) { fprintf(stderr,"No host display configuration for %d-bit depth fixture\n",bits);exit(1); }
    EGLint surfaceAttributes[]={EGL_WIDTH,32,EGL_HEIGHT,32,EGL_NONE};
    EGLint contextAttributes[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};
    EGLSurface surface=eglCreatePbufferSurface(display,config,surfaceAttributes);
    EGLContext context=eglCreateContext(display,config,EGL_NO_CONTEXT,contextAttributes);
    if(surface==EGL_NO_SURFACE || context==EGL_NO_CONTEXT || !eglMakeCurrent(display,surface,surface,context)) exit(1);
    GLuint program=glCreateProgram();glAttachShader(program,shader(GL_VERTEX_SHADER,vertex_source));
    glAttachShader(program,shader(GL_FRAGMENT_SHADER,fragment_source));glLinkProgram(program);
    GLint linked;glGetProgramiv(program,GL_LINK_STATUS,&linked);if(!linked) exit(1);
    // EGL window formats can omit depth16; GLES2 requires depth16 renderbuffers.
    GLuint framebuffer,colorBuffer,depthBuffer;
    glGenFramebuffers(1,&framebuffer);glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
    glGenRenderbuffers(1,&colorBuffer);glBindRenderbuffer(GL_RENDERBUFFER,colorBuffer);
    glRenderbufferStorage(GL_RENDERBUFFER,GL_RGBA4,32,32);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_RENDERBUFFER,colorBuffer);
    glGenRenderbuffers(1,&depthBuffer);glBindRenderbuffer(GL_RENDERBUFFER,depthBuffer);
    glRenderbufferStorage(GL_RENDERBUFFER,bits==16?GL_DEPTH_COMPONENT16:0x81A6,32,32);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depthBuffer);
    if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) exit(1);
    GLint actualBits;glGetIntegerv(GL_DEPTH_BITS,&actualBits);if(actualBits!=bits) exit(1);
    glUseProgram(program);glViewport(0,0,32,32);glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LESS);
    glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
    float identity[]={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
    float f=2.41421356237f;
    float projection[]={f,0,0,0,0,f,0,0,0,0,-(far+near)/(far-near),-1,0,0,-2*far*near/(far-near),0};
    glUniformMatrix4fv(glGetUniformLocation(program,"uMvp"),1,GL_FALSE,projection);
    glUniformMatrix4fv(glGetUniformLocation(program,"uModel"),1,GL_FALSE,identity);
    glUniform1f(glGetUniformLocation(program,"uLit"),0);
    GLint position=glGetAttribLocation(program,"aPosition"),normal=glGetAttribLocation(program,"aNormal");
    glVertexAttrib3f(normal,0,0,1);glEnableVertexAttribArray(position);
    float back[]={-1,-1,-4.8001f,1,-1,-4.8001f,0,1,-4.8001f};
    float front[]={-1,-1,-4.8f,1,-1,-4.8f,0,1,-4.8f};
    glUniform4f(glGetUniformLocation(program,"uColor"),0,0,1,1);
    glVertexAttribPointer(position,3,GL_FLOAT,GL_FALSE,0,back);glDrawArrays(GL_TRIANGLES,0,3);
    glUniform4f(glGetUniformLocation(program,"uColor"),1,0,0,1);
    glVertexAttribPointer(position,3,GL_FLOAT,GL_FALSE,0,front);glDrawArrays(GL_TRIANGLES,0,3);
    GLubyte pixel[4];glReadPixels(16,16,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
    if(glGetError()!=GL_NO_ERROR) exit(1);
    int red=pixel[0]>200 && pixel[2]<20;
    printf("depth=%d near=%.6f far=%.6f center=(%u,%u,%u) %s\n",bits,near,far,pixel[0],pixel[1],pixel[2],red?"front visible":"depth collision");
    eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
    eglDestroyContext(display,context);eglDestroySurface(display,surface);
    return red;
}
int main(int argc,char **argv) {
    if(argc!=3) return 1;
    EGLDisplay display=eglGetDisplay(EGL_DEFAULT_DISPLAY);EGLint major,minor;
    if(!eglInitialize(display,&major,&minor)) { fprintf(stderr,"Cannot initialize host EGL: 0x%x\n",eglGetError());return 1; }
    eglBindAPI(EGL_OPENGL_ES_API);
    printf("Host EGL %d.%d\n",major,minor);
    int old=render(display,16,0.0048f,14.8f);
    int fixed16=render(display,16,strtof(argv[1],NULL),strtof(argv[2],NULL));
    int fixed24=render(display,24,strtof(argv[1],NULL),strtof(argv[2],NULL));
    eglTerminate(display);
    if(old || !fixed16 || !fixed24) { fprintf(stderr,"Depth regression was not reproduced and fixed as expected\n");return 1; }
    return 0;
}
