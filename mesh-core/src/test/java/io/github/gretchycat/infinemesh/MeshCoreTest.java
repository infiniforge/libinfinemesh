package io.github.gretchycat.infinemesh;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MeshCoreTest {
    private static final DecodeLimits LIMITS=DecodeLimits.DEFAULT;
    private ModelDocument gcode(String text) throws IOException { return new GcodeDecoder().decode(text.getBytes(StandardCharsets.US_ASCII),LIMITS); }
    private byte[] binary(String header,int count) {
        ByteBuffer b=ByteBuffer.allocate(84+count*50).order(ByteOrder.LITTLE_ENDIAN);
        b.put(header.getBytes(StandardCharsets.US_ASCII));b.position(80);b.putInt(count);
        for(int i=0;i<count;i++) { b.putFloat(0).putFloat(0).putFloat(1);for(float f:new float[]{0,0,0,2,0,0,0,3,0}) b.putFloat(f);b.putShort((short)0); }
        return b.array();
    }
    private String ascii() { return "solid test\nfacet normal 0 0 0\nouter loop\nvertex 0 0 0\nvertex 2 0 0\nvertex 0 3 0\nendloop\nendfacet\nendsolid test\n"; }
    @Test public void binaryHeaderMayStartWithSolid() throws Exception {
        ModelDocument doc=new StlDecoder().decode(binary("solid binary file",1),LIMITS);
        assertEquals("Binary STL",doc.format);assertEquals(1,doc.mesh.triangleCount());assertEquals(3,doc.bounds().maxY(),0);
    }
    @Test public void asciiComputesNormalsFromGeometry() throws Exception {
        MeshGeometry mesh=new StlDecoder().decode(ascii().getBytes(StandardCharsets.US_ASCII),LIMITS).mesh;
        assertArrayEquals(new float[]{0,0,1,0,0,1,0,0,1},mesh.normals(false),0);
    }
    @Test public void asciiRejectsIncompleteTrianglesAndMissingEnd() {
        assertThrows(IOException.class,()->new StlDecoder().decode(ascii().replace("vertex 0 3 0\n","").getBytes(),LIMITS));
        assertThrows(IOException.class,()->new StlDecoder().decode(ascii().replace("endsolid test","").getBytes(),LIMITS));
    }
    @Test public void binaryRejectsTruncationNanAndUnboundedCount() {
        assertThrows(IOException.class,()->new StlDecoder().decode(Arrays.copyOf(binary("binary",1),100),LIMITS));
        byte[] nan=binary("binary",1);ByteBuffer.wrap(nan).order(ByteOrder.LITTLE_ENDIAN).putFloat(96,Float.NaN);
        assertThrows(IOException.class,()->new StlDecoder().decode(nan,LIMITS));
        byte[] huge=binary("binary",1);ByteBuffer.wrap(huge).order(ByteOrder.LITTLE_ENDIAN).putInt(80,-1);
        assertThrows(IOException.class,()->new StlDecoder().decode(huge,LIMITS));
        assertThrows(IOException.class,()->new StlDecoder().decode(binary("binary",2),new DecodeLimits(1000,1,1)));
    }
    @Test public void smoothNormalsWeldExactSharedCoordinatesIncludingNegativeZero() {
        MeshGeometry m=new MeshGeometry(new float[]{0,0,0,1,0,0,0,1,0, -0f,0,0,0,0,1,1,0,0});
        float[] flat=m.normals(false),smooth=m.normals(true);
        assertEquals(1,flat[2],0);assertEquals(0,flat[1],0);
        assertEquals(Math.sqrt(0.5),smooth[1],1e-6);assertEquals(Math.sqrt(0.5),smooth[2],1e-6);
        assertEquals(smooth[1],smooth[10],0);
    }
    @Test public void geometryIsImmutableAndDegenerateNormalsAreFinite() {
        float[] p={0,0,0,0,0,0,0,0,0};MeshGeometry m=new MeshGeometry(p);p[0]=42;
        assertEquals(0,m.positions()[0],0);assertEquals(1,m.normals(true)[2],0);
    }
    @Test public void gcodeFeedrateAndExtrusionProduceTimedTravelAndPrint() throws Exception {
        Toolpath t=gcode("G21\nG90\nM82\nG1 X60 F600\nG1 Y60 E1\n").toolpath;
        assertEquals(2,t.segmentCount());assertArrayEquals(new boolean[]{false,true},t.extrusion());
        assertEquals(12,t.durationSeconds(),1e-6);assertArrayEquals(new float[]{30,0,0},t.headAt(3),1e-6f);
        assertEquals(1,t.completedSegments(6));assertEquals(2,t.completedSegments(12));
    }
    @Test public void g92ChangesCoordinatesWithoutMovingThePhysicalHead() throws Exception {
        Toolpath t=gcode("G1 X10 E4 F600\nG92 X0 E0\nG1 X5 E1\n").toolpath;
        assertArrayEquals(new float[]{15,0,0},t.headAt(t.durationSeconds()),1e-6f);
        assertTrue(t.extrusion()[1]);
    }
    @Test public void g91AndExtruderOverrideAndG90Reset() throws Exception {
        Toolpath t=gcode("G1 X10 E1\nG91\nM82\nG1 X5 E0.5\nG90\nG1 X20 E1\n").toolpath;
        assertArrayEquals(new boolean[]{true,false,true},t.extrusion());
        assertArrayEquals(new float[]{20,0,0},t.headAt(t.durationSeconds()),0);
    }
    @Test public void inchesAndModalMovesAndCompactWords() throws Exception {
        Toolpath t=gcode("G20\nG90\nG1X1F60\nY1\n").toolpath;
        assertEquals(2,t.durationSeconds(),1e-6);assertArrayEquals(new float[]{25.4f,25.4f,0},t.headAt(2),1e-5f);
    }
    @Test public void dwellRetractionAndHomingArePreviewed() throws Exception {
        ModelDocument d=gcode("G1 X10 F600\nG4 P2000\nG1 E-1\nG28 X\n");Toolpath t=d.toolpath;
        assertEquals(4,t.segmentCount());assertEquals(4.1,t.durationSeconds(),1e-6);
        assertArrayEquals(new float[]{10,0,0},t.headAt(2),1e-6f);
        assertArrayEquals(new float[]{0,0,0},t.headAt(5),1e-6f);
        assertTrue(d.warnings.stream().anyMatch(w->w.contains("homing")));
    }
    @Test public void xyArcsIncludeExactEndpointAndHelicalHeight() throws Exception {
        Toolpath t=gcode("G1 X10 F600\nM83\nG3 X-10 Y0 Z2 I-10 J0 E2\n").toolpath;
        assertTrue(t.segmentCount()>90);assertArrayEquals(new float[]{-10,0,2},t.headAt(t.durationSeconds()),0);
        assertEquals(1+Math.hypot(Math.PI*10,2)/10,t.durationSeconds(),1e-6);
        assertTrue(t.extrusion()[1]);assertEquals(10,t.bounds().maxY(),1e-4);
    }
    @Test public void fullCircleAndClockwiseArcs() throws Exception {
        Toolpath t=gcode("G1 X10 F600\nG2 I-10 J0\n").toolpath;
        assertEquals(1+Math.PI*2,t.durationSeconds(),1e-6);assertEquals(-10,t.bounds().minX(),1e-4);
    }
    @Test public void commentsLineNumbersAndMachineCommands() throws Exception {
        ModelDocument d=gcode("; comment\nN1 G21*17\nM104 S200\nM117 Printing model\nG1 X10 (move) F600 ;ok\n");
        assertEquals(1,d.toolpath.segmentCount());assertTrue(d.warnings.size()>1);
    }
    @Test public void unknownCommandsAndMacrosDoNotAbortOrAlterModalState() throws Exception {
        Toolpath path=gcode("G1 X10 F600\nM999 ARBITRARY TEXT\nG999 X900 F1\nG29.1 X999\nT1\nSTART_PRINT BED_TEMP=60\nX20\nG1 X30 M555 STRANGE G1 X40\n").toolpath;
        assertEquals(4,path.segmentCount());assertEquals(4,path.durationSeconds(),1e-6);
        assertArrayEquals(new float[]{40,0,0},path.headAt(99),0);
    }
    @Test public void ignoredCommandsAreReportedOnceAndExtraParametersAreIgnored() throws Exception {
        ModelDocument d=gcode("G1 X10 Q123 F600\nM999 X100\nM999 Y200\nG1 X20\n");
        assertEquals(2,d.toolpath.segmentCount());
        assertEquals(1,d.warnings.stream().filter(w->w.contains("were ignored")).count());
        assertThrows(IOException.class,()->gcode("G1 Xnan\n"));
        assertThrows(IOException.class,()->gcode("G1 X10 F0\n"));
        assertThrows(IOException.class,()->gcode("G1 X1 X2\n"));
        assertThrows(IOException.class,()->gcode("G2 X10 R5\n"));
    }
    @Test public void invalidTextAndCommandsDoNotAbortValidMotion() throws Exception {
        ModelDocument doc=gcode("X999\n123? invalid\nG1 X10 F600\nG1 Xnan G1 X20\nG1 X1 X2\nG1 X900 F0\nG2 X900 R5 F1\nX30\nG4 S-1\nG1 X40 trailing junk\nG1 X40\n");
        assertEquals(4,doc.toolpath.segmentCount());
        assertEquals(4,doc.toolpath.durationSeconds(),1e-6);
        assertArrayEquals(new float[]{40,0,0},doc.toolpath.headAt(99),0);
        assertEquals(1,doc.warnings.stream().filter(w->w.startsWith("Invalid G-code text")).count());
    }
    @Test public void invalidMovesRetainPriorFeedAndMotionMode() throws Exception {
        Toolpath t=gcode("G1 X10 F600\nG20\nG92 X1 Y"+"9".repeat(307)+"\nG21\nG2 X20 R5 F1\nX20\nG1 X"+"9".repeat(350)+" F2\nX30\nG1 Y0 I1 BAD\nX40\n").toolpath;
        assertEquals(4,t.segmentCount());assertEquals(4,t.durationSeconds(),1e-6);
        assertArrayEquals(new float[]{40,0,0},t.headAt(99),0);
    }
    @Test public void invalidOnlyFilesAndResourceFailuresStillHaveUsefulErrors() throws Exception {
        assertThrows(IOException.class,()->gcode("invalid text\nG1 Xnan\n"));
        assertThrows(IOException.class,()->new GcodeDecoder().decode("G1 X10\nG2 I-10 J0\n".getBytes(),new DecodeLimits(1000,1,2)));
        Thread.currentThread().interrupt();
        try { assertThrows(IOException.class,()->gcode("junk\nG1 X1\n")); }
        finally {Thread.interrupted();}
    }
    @Test public void modelAliasesShareTheSameAssociation() {
        for(String ext:ModelFormats.GCODE_EXTENSIONS) assertEquals("text/x-gcode",ModelFormats.mimeType("part."+ext.toUpperCase(Locale.ROOT),"text/plain"));
        assertEquals("model/stl",ModelFormats.mimeType("part.STL","application/octet-stream"));
        assertFalse(ModelFormats.supports("part.txt","text/plain"));
    }
    @Test public void decodeBudgetsAreEnforced() {
        assertThrows(IOException.class,()->new GcodeDecoder().decode("G1 X1\nG1 X2\n".getBytes(),new DecodeLimits(100,1,1)));
        assertThrows(IOException.class,()->new ModelLoader().load(new ByteArrayInputStream(new byte[101]),"x.stl","",new DecodeLimits(100,1,1)));
    }
    @Test public void gcodeIdentityOutranksInvalidTextResemblingAnotherFormat() throws Exception {
        byte[] text="solid invalid header\n???\nG1 X10 F600\n".getBytes(StandardCharsets.US_ASCII);
        assertEquals("G-code",new ModelLoader().load(new ByteArrayInputStream(text),"part.gcode","text/plain",LIMITS).format);
        assertEquals("G-code",new ModelLoader().load(new ByteArrayInputStream(text),"opaque","application/x-gcode",LIMITS).format);
    }
    @Test public void loaderUsesNamesMimeAndCustomDecoders() throws Exception {
        assertNotNull(new ModelLoader().load(new ByteArrayInputStream(binary("binary",1)),"TEST.STL","",LIMITS).mesh);
        assertNotNull(new ModelLoader().load(new ByteArrayInputStream("G1 X1".getBytes()),"opaque","text/x-gcode",LIMITS).toolpath);
        assertThrows(IOException.class,()->new ModelLoader().load(new ByteArrayInputStream(new byte[0]),"x.obj","",LIMITS));
        ModelDecoder custom=new ModelDecoder() {
            public boolean supports(String n,String m,byte[] prefix) { return n.endsWith(".custom"); }
            public ModelDocument decode(byte[] data,DecodeLimits limits) throws IOException { return gcode("G1 X1"); }
        };
        assertNotNull(new ModelLoader(List.of(custom)).load(new ByteArrayInputStream(new byte[0]),"test.custom","",LIMITS).toolpath);
    }
}
