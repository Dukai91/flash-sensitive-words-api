package za.co.flash.sensitivewords.config;

import java.util.Locale;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
public class ProductionDatabaseGuard implements InitializingBean {
    private final DataSourceProperties properties;

    public ProductionDatabaseGuard(DataSourceProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        String url = properties.determineUrl().toLowerCase(Locale.ROOT).replace(" ", "");
        String username = properties.determineUsername();
        if (username == null || username.isBlank() || username.strip().equalsIgnoreCase("sa")
                || !(url.contains(";encrypt=true") || url.contains(";encrypt=strict"))
                || !url.contains(";trustservercertificate=false")
                || url.contains(";trustservercertificate=true") || url.contains(";encrypt=false")
                || url.contains(";encrypt=optional")) {
            throw new IllegalStateException("Production requires a non-sa identity and an encrypted JDBC URL with certificate validation");
        }
    }
}
