package io.github.gretchycat.infinemesh;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Common Cartesian FDM preview dialect. Never connects to a machine. */
public final class GcodeDecoder implements ModelDecoder {
    private static final Set<Integer> IGNORED_MACHINE_COMMANDS = new HashSet<>(Arrays.asList(18,84,104,109,140,190,106,107,105,114,115,201,203,204,205,400,900));
    private static final Pattern COMMAND=Pattern.compile("\"[^\"]*\"|'[^']*'|(?<![A-Z_])([GMT])\\s*([-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))");
    private static final Pattern WORD=Pattern.compile("([A-Z])\\s*([-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))");
    public boolean supports(String name,String mime,byte[] prefix) {
        return "text/x-gcode".equals(ModelFormats.mimeType(name,mime));
    }
    public ModelDocument decode(byte[] bytes,DecodeLimits limits) throws IOException {
        State s=new State(limits); int lineNo=0;
        try(BufferedReader reader=new BufferedReader(new StringReader(new String(bytes,StandardCharsets.US_ASCII)))) {
            String raw;
            while((raw=reader.readLine())!=null) {
                lineNo++;
                if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Load cancelled");
                String line=raw.split("[;*]",2)[0].replaceAll("\\([^)]*\\)"," ").trim().toUpperCase(Locale.ROOT);
                if(line.isEmpty() || line.equals("%")) continue;
                line=line.replaceFirst("^N\\d+\\s*","");
                // Display text has no effect on motion, and need not be numeric.
                if(line.matches("M(?:117|118)\\b.*")) continue;
                if(line.startsWith("G28")) line=line.replaceAll("([XYZ])(?=\\s|$)","$10");
                Matcher commands=COMMAND.matcher(line);
                String command=null;int body=0;
                while(commands.find()) {
                    if(commands.group(1)==null) continue; // quoted strings are arguments, not commands
                    if(command!=null) s.block(command,line.substring(body,commands.start()));
                    else if(!line.substring(0,commands.start()).trim().isEmpty()) {
                        s.ignored();break;
                    }
                    command=commands.group(1)+commands.group(2);body=commands.end();
                }
                if(command!=null) s.block(command,line.substring(body));
                else if(line.matches("[A-Z_]{2,}.*")) s.ignored(); // firmware macros
                else if(s.motion!=null) s.block(s.motion,line);
                else throw new IOException("Coordinates without a motion mode");
            }
        } catch(IllegalArgumentException e) { throw new IOException("G-code line "+lineNo+": "+e.getMessage(),e); }
          catch(IOException e) { throw new IOException("G-code line "+lineNo+": "+e.getMessage(),e); }
        if(s.ends.isEmpty()) throw new IOException("G-code contains no previewable motion");
        boolean[] extrude=new boolean[s.extrusions.size()]; double[] ends=new double[s.ends.size()];
        for(int i=0;i<ends.length;i++) { extrude[i]=s.extrusions.get(i); ends[i]=s.ends.get(i); }
        return new ModelDocument("G-code",null,new Toolpath(s.points.array(),extrude,ends),new ArrayList<>(s.warnings));
    }
    private static final class State {
        final DecodeLimits limits; final FloatBuilder points=new FloatBuilder();
        final List<Boolean> extrusions=new ArrayList<>(); final List<Double> ends=new ArrayList<>();
        final Set<String> warnings=new LinkedHashSet<>();
        final double[] pos=new double[4], offset=new double[4];
        double units=1, feed=1200, time=0; boolean absolute=true; Boolean eAbsolute=null; String motion=null;
        State(DecodeLimits limits) {
            this.limits=limits;
            warnings.add("Kinematic preview: assumes origin, one extruder, and 1200 mm/min until F is set. Acceleration, heating, leveling and machine limits are not simulated.");
        }
        void ignored() { warnings.add("Unsupported G-code commands or parameters were ignored; motion and machine-state changes may be omitted."); }
        boolean supported(String cmd) {
            double code=Double.parseDouble(cmd.substring(1));
            if(code!=Math.rint(code) || code<0 || code>Integer.MAX_VALUE) return false;
            int n=(int)code;
            return switch(cmd.charAt(0)) {
                case 'G' -> n<=4 || n==17 || n==20 || n==21 || n==28 || n==90 || n==91 || n==92;
                case 'M' -> n==82 || n==83 || IGNORED_MACHINE_COMMANDS.contains(n);
                case 'T' -> n==0;
                default -> false;
            };
        }
        void block(String cmd,String body) throws IOException {
            if(!supported(cmd)) { ignored();return; }
            int n=(int)Double.parseDouble(cmd.substring(1));
            if(cmd.charAt(0)=='M' && IGNORED_MACHINE_COMMANDS.contains(n)) { command(cmd,Collections.emptyMap());return; }
            Matcher words=WORD.matcher(body);int end=0;Map<Character,Double> p=new HashMap<>();
            while(words.find()) {
                if(!body.substring(end,words.start()).trim().isEmpty()) throw new IOException("Invalid parameter in "+cmd);
                char letter=words.group(1).charAt(0);double value=Double.parseDouble(words.group(2));
                if(!Double.isFinite(value)) throw new IOException("Non-finite G-code value");
                if(p.put(letter,value)!=null) throw new IOException("Duplicate G-code parameter "+letter);
                end=words.end();
            }
            if(!body.substring(end).trim().isEmpty()) throw new IOException("Invalid parameter in "+cmd);
            command(cmd,p);
        }
        void command(String cmd,Map<Character,Double> p) throws IOException {
            double code=Double.parseDouble(cmd.substring(1));
            if(code!=Math.rint(code)) { ignored();return; }
            int n=(int)code;
            switch(cmd.charAt(0)) {
                case 'T' -> { if(n!=0) ignored(); }
                case 'M' -> {
                    if(n==82) eAbsolute=true;
                    else if(n==83) eAbsolute=false;
                    else if(IGNORED_MACHINE_COMMANDS.contains(n))
                        warnings.add("Machine settings and thermal/fan commands are omitted from preview timing.");
                    else ignored();
                }
                case 'G' -> {
                    switch(n) {
                        case 0,1,2,3 -> { motion="G"+n; move(n,p); }
                        case 4 -> { double delay=p.getOrDefault('S',p.getOrDefault('P',0.0)/1000.0); if(delay<0) throw new IOException("Negative dwell"); add(pos.clone(),false,delay); }
                        case 17 -> { /* XY plane */ }
                        case 20 -> units=25.4;
                        case 21 -> units=1;
                        case 90 -> { absolute=true; eAbsolute=null; }
                        case 91 -> { absolute=false; eAbsolute=null; }
                        case 92 -> { for(int a=0;a<4;a++) { char axis="XYZE".charAt(a); if(p.containsKey(axis)) offset[a]=pos[a]-p.get(axis)*units; } }
                        case 28 -> {
                            warnings.add("G28 homing is approximated at XYZ zero; actual machine homing positions are unknown.");
                            double[] target=pos.clone(); boolean all=!p.containsKey('X')&&!p.containsKey('Y')&&!p.containsKey('Z');
                            for(int a=0;a<3;a++) if(all || p.containsKey("XYZ".charAt(a))) { target[a]=0; offset[a]=0; }
                            add(target,false,distance(pos,target)*60/feed);
                        }
                        default -> ignored();
                    }
                }
                default -> ignored();
            }
        }
        void move(int mode,Map<Character,Double> p) throws IOException {
            for(char key:p.keySet()) if("XYZEFIJR".indexOf(key)<0) ignored();
            if(p.containsKey('F')) { feed=p.get('F')*units; if(feed<=0 || !Double.isFinite(feed)) throw new IOException("Feedrate must be positive"); }
            double[] target=pos.clone();
            for(int a=0;a<4;a++) {
                char axis="XYZE".charAt(a);
                if(p.containsKey(axis)) {
                    boolean abs=a==3 && eAbsolute!=null ? eAbsolute : absolute;
                    target[a]=p.get(axis)*units+(abs?offset[a]:pos[a]);
                    if(!Double.isFinite(target[a]) || Math.abs(target[a])>Float.MAX_VALUE) throw new IOException("Coordinate outside preview range");
                }
            }
            boolean extrude=target[3]>pos[3] && mode!=0;
            if(mode<2) {
                double len=distance(pos,target);
                if(len==0) len=Math.abs(target[3]-pos[3]);
                if(len>0) add(target,extrude,len*60/feed);
                else System.arraycopy(target,0,pos,0,4);
                return;
            }
            if(p.containsKey('R')) throw new IOException("Radius-form arcs are not yet supported; use XY I/J arcs");
            if(!p.containsKey('I') && !p.containsKey('J')) throw new IOException("Arc requires I/J center offsets");
            double sx=pos[0], sy=pos[1], sz=pos[2], se=pos[3];
            double cx=sx+p.getOrDefault('I',0.0)*units, cy=sy+p.getOrDefault('J',0.0)*units;
            double radius=Math.hypot(sx-cx,sy-cy), endRadius=Math.hypot(target[0]-cx,target[1]-cy);
            if(radius==0 || Math.abs(radius-endRadius)>Math.max(0.01,radius*0.001)) throw new IOException("Invalid arc center/radius");
            double start=Math.atan2(sy-cy,sx-cx), sweep=Math.atan2(target[1]-cy,target[0]-cx)-start;
            if(mode==2) { while(sweep>=-1e-12) sweep-=Math.PI*2; }
            else { while(sweep<=1e-12) sweep+=Math.PI*2; }
            double stepsNeeded=Math.max(Math.ceil(Math.abs(sweep)/(Math.PI/90)),Math.ceil(Math.abs(sweep)*radius));
            if(stepsNeeded>limits.maxSegments()-ends.size()) throw new IOException("Arc exceeds preview segment limit");
            int steps=Math.max(1,(int)stepsNeeded);
            double duration=Math.hypot(Math.abs(sweep)*radius,target[2]-sz)*60/feed;
            for(int i=1;i<=steps;i++) {
                double f=(double)i/steps, angle=start+sweep*f;
                double[] next={cx+Math.cos(angle)*radius,cy+Math.sin(angle)*radius,sz+(target[2]-sz)*f,se+(target[3]-se)*f};
                if(i==steps) next=target;
                add(next,extrude,duration/steps);
            }
        }
        void add(double[] target,boolean extrude,double duration) throws IOException {
            if(ends.size()>=limits.maxSegments()) throw new IOException("G-code exceeds preview segment limit");
            if(!Double.isFinite(duration) || duration<0 || !Double.isFinite(time+duration)) throw new IOException("Invalid preview duration");
            for(int a=0;a<3;a++) if(!Double.isFinite(target[a]) || Math.abs(target[a])>Float.MAX_VALUE) throw new IOException("Coordinate outside preview range");
            points.add((float)pos[0],(float)pos[1],(float)pos[2],(float)target[0],(float)target[1],(float)target[2]);
            extrusions.add(extrude); time+=duration; ends.add(time); System.arraycopy(target,0,pos,0,4);
        }
        static double distance(double[] a,double[] b) { return Math.hypot(Math.hypot(a[0]-b[0],a[1]-b[1]),a[2]-b[2]); }
    }
}
