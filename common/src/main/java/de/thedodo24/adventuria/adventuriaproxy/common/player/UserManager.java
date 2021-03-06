package de.thedodo24.adventuria.adventuriaproxy.common.player;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDatabase;
import com.arangodb.util.MapBuilder;
import com.arangodb.velocypack.VPackSlice;
import com.google.common.collect.Lists;
import de.thedodo24.adventuria.adventuriaproxy.common.arango.CollectionManager;
import lombok.Getter;
import net.md_5.bungee.api.ProxyServer;

import java.util.*;

@Getter
public class UserManager extends CollectionManager<User, UUID> {

    private Map<String, UUID> names = new HashMap<>();
    private ArangoDatabase database;

    public UserManager(ArangoDatabase database) {
        super("user", User::new, database, (key, obj) -> ProxyServer.getInstance().getPlayer(key) != null);
        this.database = database;
    }

    public User getByName(String s) {
        if(names.containsKey(s))
            return this.getOrGenerate(names.get(s));
        ArangoCursor<VPackSlice> cursor = getDatabase().query("FOR player IN " + this.collection.name() + " FILTER LIKE (player.name, @name, true) RETURN {uniqueId: player._key}",
                new MapBuilder().put("name", s).get(), null, VPackSlice.class);
        UUID uniqueId = null;
        if(cursor.hasNext())
            uniqueId = UUID.fromString(cursor.next().get("uniqueId").getAsString());
        this.closeCursor(cursor);
        if(uniqueId != null) {
            names.put(s, uniqueId);
            return this.getOrGenerate(uniqueId);
        }
        return null;
    }

    public List<User> getByIP(String s) {
        ArangoCursor<VPackSlice> cursor = getDatabase().query("FOR player IN " + this.collection.name() + " FILTER LIKE (player.ip, @ip, true) RETURN {uniqueId: player._key}",
                new MapBuilder().put("ip", s).get(), null, VPackSlice.class);
        List<UUID> uniqueIds = Lists.newArrayList();
        while(cursor.hasNext())
            uniqueIds.add(UUID.fromString(cursor.next().get("uniqueId").getAsString()));
        this.closeCursor(cursor);
        List<User> users = Lists.newArrayList();
        for(UUID uniqueId : uniqueIds) {
            if(uniqueId != null)
                users.add(this.getOrGenerate(uniqueId));
        }
        return users;
    }

    public List<User> getUsers() {
        ArangoCursor<VPackSlice> cursor = getDatabase().query("FOR player IN " + this.collection.name() + " RETURN {uniqueId: player._key}",
                VPackSlice.class);
        List<User> list = Lists.newArrayList();
        while(cursor.hasNext()) {
            UUID uuid = UUID.fromString(cursor.next().get("uniqueId").getAsString());
            list.add(get(uuid));
        }
        this.closeCursor(cursor);
        return list;
    }

    @Override
    public User uncache(UUID key) {
        User user = super.uncache(key);
        names.remove(user.getName());
        return user;
    }

    public void disableSave() {
        this.cache.values().forEach(this::save);
    }

    public void update(User user) {
        this.save(user);
        this.uncache(user.getKey());
    }


}
