package io.github.gretchycat.infinemesh;
import java.util.Arrays;
final class FloatBuilder {
    private float[] values=new float[1024]; private int size;
    void add(float... floats) {
        if(size+floats.length>values.length) values=Arrays.copyOf(values,Math.max(size+floats.length,values.length+values.length/2));
        for(float f:floats) values[size++]=f;
    }
    float[] array() { return Arrays.copyOf(values,size); }
    int size() { return size; }
}
