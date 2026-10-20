package com.farmorchestra.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets a POST request through only if it carries the correct {@code DEVICE-API-KEY} header.
 * Wrong or missing key: HTTP 401, and the controller is never called.
 */
@Component
public class DeviceKeyFilter extends OncePerRequestFilter {
    private final byte[] expected;

    /**
     * Reads the key once at startup.
     *
     * <p>It fails fast if a server starts without a key, since it would either
     * reject every device or, worse, accept an empty header. Crashing at startup makes the
     * misconfiguration obvious immediately.
     *
     * @param key the secret from {@code farm.device-api-key} (env {@code DEVICE_API_KEY})
     * @throws IllegalStateException if the key is blank; the application will not start
     */
    public DeviceKeyFilter(@Value("${farm.device-api-key}") String key) {
        if (key.isBlank()) throw new IllegalStateException("Set DEVICE_API_KEY before starting the API");
        expected = key.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Skips everything except POST. Reads stay open; only writes need the device key.
     *
     * @param request the incoming request
     * @return {@code true} to skip the key check
     */
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod());
    }

    /**
     * Compares the supplied key with the expected one and stops the request on mismatch.
     *
     * <p>Using {@link MessageDigest#isEqual} instead of {@code equals} because {@code String.equals}
     * stops at the first different character, so an attacker could time responses and guess the
     * key one character at a time. {@code isEqual} takes the same time however many characters
     * match (a "constant-time" comparison).
     *
     * @param request  the incoming request
     * @param response used to send 401 on failure
     * @param chain    the rest of the request pipeline, called only on success
     * @throws ServletException if a later filter fails
     * @throws IOException      if writing the response fails
     */
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader("DEVICE-API-KEY");
        if (supplied == null || !MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
