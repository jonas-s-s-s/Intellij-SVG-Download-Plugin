package svg.downloader.utils

import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.JPEGTranscoder
import java.io.ByteArrayOutputStream
import java.io.StringReader

fun svgToJpegByteArray(
    svgString: String,
    width: Float = 80f,   // Default width
    height: Float = 80f,  // Default height
    quality: Float = 0.67f // Default JPEG quality (0.0f - 1.0f)
): ByteArray {
    return ByteArrayOutputStream().use { outputStream ->
        val transcoder = JPEGTranscoder().apply {
            addTranscodingHint(JPEGTranscoder.KEY_WIDTH, width)
            addTranscodingHint(JPEGTranscoder.KEY_HEIGHT, height)
            addTranscodingHint(JPEGTranscoder.KEY_QUALITY, quality)
        }

        val input = TranscoderInput(StringReader(svgString))
        val output = TranscoderOutput(outputStream)

        transcoder.transcode(input, output)
        outputStream.toByteArray()
    }
}