package hudson.model;

import hudson.XmlFile;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.IdStrategy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted({NoExternalUse.class})
@Deprecated
/* loaded from: UserIdMapper.class */
public class UserIdMapper {
    private static final XStream2 XSTREAM = new XStream2();
    private static final Logger LOGGER = Logger.getLogger(UserIdMapper.class.getName());
    private Map<String, String> idToDirectoryNameMap = new ConcurrentHashMap();

    private UserIdMapper() {
    }

    static void migrate() throws IOException {
        IdStrategy idStrategy = User.idStrategy();
        File usersDirectory = User.getRootDir();
        UserIdMapper data = new UserIdMapper();
        XmlFile mapperXml = new XmlFile(XSTREAM, new File(usersDirectory, "users.xml"));
        if (mapperXml.exists()) {
            LOGGER.info(() -> {
                return "migrating " + String.valueOf(mapperXml);
            });
            mapperXml.unmarshal(data);
            for (Map.Entry<String, String> entry : data.idToDirectoryNameMap.entrySet()) {
                String idKey = entry.getKey();
                String directoryName = entry.getValue();
                try {
                    File oldDirectory = new File(usersDirectory, directoryName);
                    XmlFile userXml = new XmlFile(User.XSTREAM, new File(oldDirectory, "config.xml"));
                    User user = (User) userXml.read();
                    if (user.id == null || !idKey.equals(idStrategy.keyFor(user.id))) {
                        user.id = idKey;
                        userXml.write(user);
                    }
                    File newDirectory = User.getUserFolderFor(user.id);
                    Files.move(oldDirectory.toPath(), newDirectory.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info(() -> {
                        return "migrated " + String.valueOf(oldDirectory) + " to " + String.valueOf(newDirectory);
                    });
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, "failed to migrate " + String.valueOf(entry), (Throwable) x);
                }
            }
            mapperXml.delete();
        }
        File[] subdirectories = usersDirectory.listFiles();
        if (subdirectories != null) {
            for (File oldDirectory2 : subdirectories) {
                if (!User.HASHED_DIRNAMES.matcher(oldDirectory2.getName()).matches()) {
                    XmlFile userXml2 = new XmlFile(User.XSTREAM, new File(oldDirectory2, "config.xml"));
                    if (userXml2.exists()) {
                        try {
                            User user2 = (User) userXml2.read();
                            String id = user2.id;
                            if (id == null) {
                                id = idStrategy.idFromFilename(oldDirectory2.getName());
                                user2.id = id;
                                userXml2.write(user2);
                            }
                            File newDirectory2 = User.getUserFolderFor(id);
                            Files.move(oldDirectory2.toPath(), newDirectory2.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            LOGGER.info(() -> {
                                return "migrated " + String.valueOf(oldDirectory2) + " to " + String.valueOf(newDirectory2);
                            });
                        } catch (Exception x2) {
                            LOGGER.log(Level.WARNING, "failed to migrate " + String.valueOf(oldDirectory2), (Throwable) x2);
                        }
                    }
                }
            }
        }
    }
}
