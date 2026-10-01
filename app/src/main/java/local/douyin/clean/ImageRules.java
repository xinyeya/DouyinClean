package local.douyin.clean;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Pure rules also exercised by the local JVM verification. */
public final class ImageRules {
    public static String normalized(String raw){
        if(raw==null)return null;
        String value=raw;
        if(value.regionMatches(true,0,"http://",0,7))value="https://"+value.substring(7);
        return allowed(value)?value:null;
    }
    public static boolean allowed(String raw) {
        try {
            URI u = new URI(raw); String h = u.getHost();
            if (!"https".equalsIgnoreCase(u.getScheme()) || h == null || u.getUserInfo()!=null || (u.getPort()!=-1 && u.getPort()!=443)) return false;
            h=h.toLowerCase(java.util.Locale.ROOT);
            for(String suffix:new String[]{"byteimg.com","douyinpic.com","pstatp.com","amemv.com","ibytedtos.com","bytedance.com","bytedance.net","bytetos.com","bytedns.net","douyincdn.com","douyinvod.com","bytecdn.cn","snssdk.com"}) {
                if(h.equals(suffix)||h.endsWith("."+suffix)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }
    public static String format(byte[] b) {
        if(b.length>=6 && (ascii(b,0,6).equals("GIF87a")||ascii(b,0,6).equals("GIF89a"))) return "gif";
        if(b.length>=8 && (b[0]&255)==137 && b[1]==80 && b[2]==78 && b[3]==71 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10) return "png";
        if(b.length>=3 && (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255) return "jpg";
        if(b.length>=12 && ascii(b,0,4).equals("RIFF") && ascii(b,8,4).equals("WEBP")) return "webp";
        if(b.length>=12&&ascii(b,4,4).equals("ftyp")){
            // A HEIF sequence can declare a generic major brand and a compatible animated brand.
            String known=brandFormat(ascii(b,8,4));
            int boxSize=(b[0]&255)<<24|(b[1]&255)<<16|(b[2]&255)<<8|(b[3]&255);
            int end=boxSize>=12?Math.min(boxSize,b.length):12;
            for(int offset=16;offset+4<=end;offset+=4){String candidate=brandFormat(ascii(b,offset,4));
                if(candidate!=null&&(!candidate.equals("heif")||known==null))known=candidate;
            }
            if(known!=null)return known;
        }
        return null;
    }
    public static String videoFormat(byte[] b) {
        if(b.length>=12 && ascii(b,4,4).equals("ftyp")) {
            String brand=ascii(b,8,4);
            if(brandFormat(brand)!=null)return null;
            return "mp4";
        }
        if(b.length>=4&&(b[0]&255)==0x1a&&(b[1]&255)==0x45&&(b[2]&255)==0xdf&&(b[3]&255)==0xa3)return "webm";
        return null;
    }
    private static String brandFormat(String brand){
        if(brand.equals("avif")||brand.equals("avis"))return "avif";
        if(brand.equals("heic")||brand.equals("heix")||brand.equals("hevc")||brand.equals("hevx"))return "heic";
        if(brand.equals("mif1")||brand.equals("msf1"))return "heif";
        if(brand.equals("vvic"))return "vvic";
        return null;
    }
    private static String ascii(byte[] b,int p,int n) { return new String(b,p,n,StandardCharsets.US_ASCII); }
    private ImageRules() {}
}
