package org.sakaiproject.content.googledrive;

import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;
import java.io.IOException;
import java.io.Serializable;


public class DBDataStoreFactory extends AbstractDataStoreFactory {
    protected <V extends Serializable> DataStore<V> createDataStore(String id) throws IOException {
        return new DBDataStore(this, id);
    }
}
