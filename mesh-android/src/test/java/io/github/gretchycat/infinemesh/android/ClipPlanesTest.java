package io.github.gretchycat.infinemesh.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClipPlanesTest {
    private long depthBin(double distance,double near,double far,int bits) {
        double depth=far/(far-near)-far*near/((far-near)*distance);
        return Math.round(depth*((1L<<bits)-1));
    }
    @Test public void nearbySheetsHaveDistinctDepthEvenWith16BitFallback() {
        ClipPlanes clip=ClipPlanes.fit(4.8f,2.35f);
        for(int bits:new int[]{16,24}) {
            assertNotEquals(depthBin(4.8,clip.near,clip.far,bits),depthBin(4.8001,clip.near,clip.far,bits));
        }
        // The previous camera/depth combination lost this separation entirely.
        assertEquals(depthBin(4.8,0.0048,14.8,16),depthBin(4.8001,0.0048,14.8,16));
    }
    @Test public void rotatedSceneAndGridFitThroughoutZoomAndAspectRange() {
        for(float aspect:new float[]{0.35f,0.6f,1,2}) for(float zoom:new float[]{0.1f,1,3,15}) {
            float radius=2.35f,distance=4.8f/Math.min(1,aspect)/zoom;
            ClipPlanes clip=ClipPlanes.fit(distance,radius);
            assertTrue(clip.near>0);assertTrue(clip.far>clip.near);
            assertTrue(clip.far>distance+radius);
            if(distance>radius+0.05f) assertTrue(clip.near<distance-radius);
        }
    }
    @Test public void zoomedInDepthAndZeroSizeGeometryRemainUsable() {
        ClipPlanes close=ClipPlanes.fit(0.32f,2.35f);
        assertTrue(close.near<0.32f);
        assertNotEquals(depthBin(0.32,close.near,close.far,24),depthBin(0.32001,close.near,close.far,24));
        ClipPlanes point=ClipPlanes.fit(4.8f,0);
        assertTrue(point.near<4.8f);assertTrue(point.far>4.8f);
    }
}
