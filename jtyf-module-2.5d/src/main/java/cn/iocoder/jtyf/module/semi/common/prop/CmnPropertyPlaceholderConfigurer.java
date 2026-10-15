package cn.iocoder.jtyf.module.semi.common.prop;

import cn.iocoder.jtyf.module.semi.common.GlobalConst;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.Properties;

/**
 * Property placeholder configurer
 * which allows properties in common.properties
 * be used in locations.
 */
public class CmnPropertyPlaceholderConfigurer extends PropertySourcesPlaceholderConfigurer implements GlobalConst {

    @Override
    public void setLocation(Resource location) {
        // add common system properties
        Resource[] locations = createLocations(location);
        // set locations as super class do
        super.setLocations(locations);
    }

    private Resource[] createLocations(Resource location) {
        // load common system properties
        Resource sysResource = new ClassPathResource(SYS_PROPERTIES_FILE);
        Properties sysProperties = loadSysProperties(sysResource);
        // create new locations
        Resource[] locations = new Resource[2];
        locations[0] = sysResource;
        locations[1] = location;
        // process placeholders from specified location
        processPlaceholders(sysProperties, locations);
        return locations;
    }

    private Properties loadSysProperties(Resource resource) {
        try {
            Properties props = PropertiesLoaderUtils.loadProperties(resource);
            return props;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void processPlaceholders(Properties sysProperties, Resource[] locations) {
        try {
            for (int i = 1; i < locations.length; i++) {
                if (locations[i] instanceof UrlResource) {
                    UrlResource resource = (UrlResource) locations[i];
                    String url = resource.getURL().toString();
                    for (Object key : sysProperties.keySet()) {
                        String keyAsString = (String) key;
                        url = url.replace(
                                placeholderPrefix + keyAsString + placeholderSuffix,
                                sysProperties.getProperty(keyAsString));
                    }
                    locations[i] = new UrlResource(url);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
