package com.artezio.forms.resources;

import java.io.InputStream;
import java.util.List;

public interface ResourceLoader {

    /**
     * Get resource by resource key.
     *
     * @param resourceKey The resource id
     * @return InputStream representing the found resource
     */
    InputStream getResource(String resourceKey);

    /**
     * Get list of existent resources
     *
     * @return List of existent resources
     */
    List<String> listResourceNames();
    
    /**
     * Returns a group id for the resources. The resource loaders which provide the same resources should have the same group id for the effective caching.  
     */
    String getGroupId();

}
