import local.douyin.clean.ImageRules;
import java.nio.charset.StandardCharsets;
public class ImageRulesCheck {
    private static int checks;
    private static void check(boolean value, String name) { checks++; if(!value)throw new AssertionError(name); }
    public static void main(String[] args) {
        check(ImageRules.allowed("https://p3.douyinpic.com/example.gif"), "valid CDN");
        check(ImageRules.allowed("https://p3.byteimg.com:443/example.gif"), "HTTPS standard port");
        for(String url:new String[]{"http://p3.byteimg.com/a", "https://byteimg.com.attacker.test/a", "https://evilbyteimg.com/a", "https://localhost/a", "https://127.0.0.1/a", "https://p3.byteimg.com:8443/a", "https://user:secret@p3.byteimg.com/a", "file:///sdcard/a", "https://p3.byteimg.com\\@attacker.test/a"}) check(!ImageRules.allowed(url), "reject " + url);
        check(!ImageRules.allowed(null), "null");
        check("gif".equals(ImageRules.format("GIF89a".getBytes(StandardCharsets.US_ASCII))), "GIF signature preserved");
        check("webp".equals(ImageRules.format("RIFFxxxxWEBP".getBytes(StandardCharsets.US_ASCII))), "animated WebP supported");
        check("png".equals(ImageRules.format(new byte[]{(byte)137,80,78,71,13,10,26,10})), "PNG");
        check("jpg".equals(ImageRules.format(new byte[]{(byte)255,(byte)216,(byte)255})), "JPEG");
        check(ImageRules.format("<html>expired</html>".getBytes(StandardCharsets.US_ASCII))==null, "expired signed URL is not saved as image");
        check(ImageRules.format(new byte[0])==null, "empty file");
        check(ImageRules.allowed("https://v3.douyinvod.com/video.mp4"), "video CDN");
        check(ImageRules.allowed("https://v.bytecdn.cn/video"), "video CDN second");
        check(!ImageRules.allowed("https://douyinvod.com.evil.test/a"), "video CDN boundary");
        check("mp4".equals(ImageRules.videoFormat(new byte[]{0,0,0,24,'f','t','y','p','i','s','o','m'})), "MP4");
        check("webm".equals(ImageRules.videoFormat(new byte[]{0x1a,0x45,(byte)0xdf,(byte)0xa3})), "WebM");
        check(ImageRules.videoFormat(new byte[]{0,0,0,24,'f','t','y','p','a','v','i','f'})==null, "AVIF is not video");
        check(ImageRules.videoFormat(new byte[]{0,0,0,24,'f','t','y','p','h','e','i','c'})==null, "HEIC is not video");
        check(ImageRules.videoFormat("<html>expired</html>".getBytes(StandardCharsets.US_ASCII))==null, "video link expired");
        check(ImageRules.videoFormat(new byte[0])==null, "empty video");
        check("https://p3.byteimg.com/a".equals(ImageRules.normalized("http://p3.byteimg.com/a")), "upgrade CDN to HTTPS");
        check(ImageRules.normalized("http://byteimg.com.attacker.test/a")==null, "reject untrusted upgrade");
        check(ImageRules.normalized(null)==null, "null candidate");
        check("avif".equals(ImageRules.format(new byte[]{0,0,0,24,'f','t','y','p','a','v','i','f'})), "AVIF image format");
        check("heic".equals(ImageRules.format(new byte[]{0,0,0,24,'f','t','y','p','h','e','i','c'})), "HEIC image format");
        for(String brand:new String[]{"hevc","hevx","mif1","msf1","vvic"}){
            byte[] header=new byte[]{0,0,0,24,'f','t','y','p',0,0,0,0};
            byte[] type=brand.getBytes(StandardCharsets.US_ASCII);System.arraycopy(type,0,header,8,4);
            check(ImageRules.format(header)!=null,"host-supported HEIF/sequence brand "+brand);
            check(ImageRules.videoFormat(header)==null,"image sequence must not become MP4 "+brand);
        }
        check("heic".equals(ImageRules.format(new byte[]{0,0,0,20,'f','t','y','p','m','i','f','1',0,0,0,0,'h','e','v','c'})),"compatible sequence brand");
        check(ImageRules.format(new byte[]{0,0,0,16,'f','t','y','p','i','s','o','m',0,0,0,0,'h','e','v','c'})==null,"ignore bytes after ftyp box");
        System.out.println("PASS: "+checks+" image/video source and format checks");
    }
}
