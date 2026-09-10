package za.co.flash.sensitivewords.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds JSON bytes before parsing, including requests without Content-Length. */
@Component
public class RequestBodyLimitFilter extends OncePerRequestFilter {
    private final int maxBytes;
    private final ObjectMapper json;

    public RequestBodyLimitFilter(SanitizationProperties properties, ObjectMapper json) {
        // Every UTF-16 unit may arrive as a six-byte JSON Unicode escape.
        this.maxBytes = properties.maxMessageLength() * 6 + 2048;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/")
                || !(request.getMethod().equals("POST") || request.getMethod().equals("PUT"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request.getContentLengthLong() > maxBytes) {
            tooLarge(response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            tooLarge(response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        throw new IllegalStateException("This synchronous API does not support non-blocking body reads");
                    }
                };
            }
        }, response);
    }

    private void tooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType("application/problem+json");
        json.writeValue(response.getOutputStream(), Map.of("type", "about:blank", "title", "Payload Too Large",
                "status", 413, "detail", "Request body exceeds " + maxBytes + " bytes"));
    }
}
