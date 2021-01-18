package org.sakaiproject.content.googledrive;

import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import java.io.*;
import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

import org.sakaiproject.db.cover.SqlService;

/*

create table nyu_t_google_datastore (
  store_id varchar2(255) not null,
  key varchar2(255) not null,
  value_hash varchar2(255) not null,
  value blob not null,
  primary key (store_id, key)
);

create index datastore_value_hash_idx on nyu_t_google_datastore(store_id, value_hash);

*/

public class DBDataStore<V extends Serializable> implements DataStore<V> {

    private DataStoreFactory factory;
    private String id;

    static Map<String, Serializable> fakeStore = new ConcurrentHashMap<>();

    public DBDataStore(DataStoreFactory factory, String id) {
        this.factory = factory;
        this.id = id;
    }

    /** Returns the data store factory. */
    public DataStoreFactory getDataStoreFactory() {
        return this.factory;
    }

    /** Returns the data store ID. */
    public String getId() {
        return this.id;
    }

    public void populateFrom(DataStore<V> otherStore) throws IOException {
        for (String key : otherStore.keySet()) {
            if (!containsKey(key)) {
                set(key, get(key));
            }
        }
    }

    /** Returns the number of stored keys. */
    public int size() throws IOException {
        return executeQuery("select count(1) from nyu_t_google_datastore where store_id = ?",
                            Arrays.asList(this.id),
                            (rs) -> {
                                if (rs.next()) {
                                    return rs.getInt(1);
                                } else {
                                    return 0;
                                }
                            });
    }

    /** Returns whether there are any stored keys. */
    public boolean isEmpty() throws IOException {
        return size() == 0;
    }

    /** Returns whether the store contains the given key. */
    public boolean containsKey(String key) throws IOException {
        return executeQuery("select count(1) from nyu_t_google_datastore where store_id = ? AND key = ?",
                            Arrays.asList(this.id, key),
                            (rs) -> {
                                if (rs.next()) {
                                    return rs.getInt(1) > 0;
                                } else {
                                    return false;
                                }
                            });
    }

    /** Returns whether the store contains the given value. */
    public boolean containsValue(V value) throws IOException {
        byte[] valueBytes = toByteArray(value);
        String hash = hexHash(valueBytes);

        return executeQuery("select count(1) from nyu_t_google_datastore where store_id = ? AND value_hash = ?",
                            Arrays.asList(this.id, hash),
                            (rs) -> {
                                if (rs.next()) {
                                    return rs.getInt(1) > 0;
                                } else {
                                    return false;
                                }
                            });
    }

    private byte[] toByteArray(V value) throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();

        try {
            ObjectOutputStream out = new ObjectOutputStream(byteOutputStream);
            out.writeObject(value);
            out.flush();

            return byteOutputStream.toByteArray();
        } finally {
            byteOutputStream.close();
        }
    }

    private V fromByteArray(byte[] bytes) throws IOException {
        ByteArrayInputStream byteInputStream = new ByteArrayInputStream(bytes);

        ObjectInputStream in = new ObjectInputStream(byteInputStream);
        try {
            return (V)in.readObject();
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            in.close();
        }
    }

    private String hexHash(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);

            StringBuilder result = new StringBuilder();
            for (byte b : digest) {
                result.append(String.format("%02x", b));
            }

            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the unmodifiable set of all stored keys.
     *
     * <p>Order of the keys is not specified.
     */
    public Set<String> keySet() throws IOException {
        final Set<String> result = new HashSet<>();

        executeQuery("select key from nyu_t_google_datastore where store_id = ?",
                     Arrays.asList(this.id),
                     (rs) -> {
                         while (rs.next()) {
                             result.add(rs.getString("key"));
                         }

                         return null;
                     });

        return result;
    }

    /** Returns the unmodifiable collection of all stored values. */
    public Collection<V> values() throws IOException {
        final ArrayList<V> result = new ArrayList<>();

        executeQuery("select value from nyu_t_google_datastore where store_id = ?",
                     Arrays.asList(this.id),
                     (rs) -> {
                         while (rs.next()) {
                             result.add(fromByteArray(rs.getBytes("value")));
                         }

                         return null;
                     });

        return result;
    }

    /**
     * Returns the stored value for the given key or {@code null} if not found.
     *
     * @param key key or {@code null} for {@code null} result
     */
    public V get(String key) throws IOException {
        return executeQuery("select value from nyu_t_google_datastore where store_id = ? AND key = ?",
                            Arrays.asList(this.id, key),
                            (rs) -> {
                                if (rs.next()) {
                                    return fromByteArray(rs.getBytes("value"));
                                }

                                return null;
                            });
    }

    /**
     * Stores the given value for the given key (replacing any existing value).
     *
     * @param key key
     * @param value value object
     */
    public DataStore<V> set(String key, V value) throws IOException {
        final byte[] valueBytes = toByteArray(value);

        executeUpdates(new Update("delete from nyu_t_google_datastore where store_id = ? AND key = ?",
                                  Arrays.asList(this.id, key)),
                       new Update("insert into nyu_t_google_datastore (store_id, key, value_hash, value) VALUES (?, ?, ?, ?)",
                                  Arrays.asList(this.id, key, hexHash(valueBytes), valueBytes)));

        return this;
    }

    /** Deletes all of the stored keys and values. */
    public DataStore<V> clear() throws IOException {
        executeUpdates(new Update("delete from nyu_t_google_datastore where store_id = ?",
                                  Arrays.asList(this.id)));

        return this;
    }

    /**
     * Deletes the stored key and value based on the given key, or ignored if the key doesn't already
     * exist.
     *
     * @param key key or {@code null} to ignore
     */
    public DataStore<V> delete(String key) throws IOException {
        executeUpdates(new Update("delete from nyu_t_google_datastore where store_id = ? AND key = ?",
                                  Arrays.asList(this.id, key)));

        return this;
    }


    private class Update {
        public String sql;
        public List<Object> params;

        public Update(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }

    }

    @FunctionalInterface
    private interface ResultSetHandler<ResultSet, R> {
        R apply(ResultSet rs) throws SQLException, IOException;
    }

    private void executeUpdates(Update ...updates) throws IOException {
        Connection conn = null;

        boolean oldAutoCommit = true;
        try {
            conn = SqlService.borrowConnection();
            oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            for (Update update : updates) {
                try (PreparedStatement ps = conn.prepareStatement(update.sql)) {
                    int idx = 0;
                    for (Object arg : update.params) {
                        idx += 1;

                        if (arg instanceof String) {
                            ps.setString(idx, (String)arg);
                        } else if (arg instanceof Integer) {
                            ps.setInt(idx, (Integer)arg);
                        } else if (arg instanceof byte[]) {
                            ps.setBytes(idx, (byte[])arg);
                        } else {
                            throw new RuntimeException("Unexpected type");
                        }
                    }

                    ps.executeUpdate();
                }
            }

            conn.commit();
        } catch (SQLException e) {
            throw new IOException(e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(oldAutoCommit);
                } catch (SQLException itried) {}
                SqlService.returnConnection(conn);
            }
        }
    }

    private <E> E executeQuery(String sql, List<Object> args, ResultSetHandler<ResultSet, E> handler) throws IOException {
        Connection conn = null;

        try {
            conn = SqlService.borrowConnection();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int idx = 0;
                for (Object arg : args) {
                    idx += 1;

                    if (arg instanceof String) {
                        ps.setString(idx, (String)arg);
                    } else if (arg instanceof Integer) {
                        ps.setInt(idx, (Integer)arg);
                    } else {
                        throw new RuntimeException("Unexpected type");
                    }
                }

                try (ResultSet rs = ps.executeQuery()) {
                    return handler.apply(rs);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        } finally {
            if (conn != null) {
                SqlService.returnConnection(conn);
            }
        }
    }
}
