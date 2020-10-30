package org.inventivetalent.customskins;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.customskins.metrics.Metrics;
import org.inventivetalent.nicknamer.api.NickNamerAPI;
import org.inventivetalent.nicknamer.api.SkinLoader;
import org.inventivetalent.pluginannotations.PluginAnnotations;
import org.inventivetalent.pluginannotations.command.Command;
import org.inventivetalent.pluginannotations.command.Completion;
import org.inventivetalent.pluginannotations.command.Permission;
import org.inventivetalent.update.spiget.SpigetUpdate;
import org.inventivetalent.update.spiget.UpdateCallback;
import org.mineskin.MineskinClient;
import org.mineskin.Model;
import org.mineskin.SkinOptions;
import org.mineskin.Visibility;
import org.mineskin.data.Skin;
import org.mineskin.data.SkinCallback;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class CustomSkins extends JavaPlugin implements Listener {

	File        skinFolder  = new File(getDataFolder(), "skins");
	Set<String> loadedSkins = new HashSet<>();

	MineskinClient skinClient;

	@Override
	public void onEnable() {
		if (!Bukkit.getPluginManager().isPluginEnabled("NickNamer")) {
			getLogger().severe("Please download & install NickNamer: https://www.spigotmc.org/resources/5341/");
			throw new RuntimeException("NickNamer not installed");
		}

		saveDefaultConfig();
		if (!skinFolder.exists()) {
			skinFolder.mkdirs();
		}

		PluginAnnotations.loadAll(this, this);

		skinClient = new MineskinClient();

		new Metrics(this);

		SpigetUpdate spigetUpdate = new SpigetUpdate(this, 25417);
		spigetUpdate.checkForUpdate(new UpdateCallback() {
			@Override
			public void updateAvailable(String s, String s1, boolean b) {
				getLogger().info("There is a new version available (" + s + "). Download it here: https://r.spiget.org/25417");
			}

			@Override
			public void upToDate() {
				getLogger().info("The plugin is up-to-date");
			}
		});
	}

	@Command(name = "createCustomSkin",
			 aliases = { "createSkin" },
			 usage = "<Name> <URL> [private] [model]",
			 description = "Create a custom skin from the specified image url",
			 min = 1,
			 max = 4,
			 fallbackPrefix = "customskins")
	@Permission("customskins.create")
	public void createSkin(final CommandSender sender, String name, String urlString, String privateUploadString, String modelString) {
		final File skinFile = new File(skinFolder, name + ".cs");
		if (sender instanceof Player && urlString == null) {
			Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
				sender.sendMessage("§eLoading skin...");
				try (Writer writer = new FileWriter(skinFile)) {
					new Gson().toJson(SkinLoader.loadSkin(sender.getName()).toJson(), writer);
				} catch (IOException e) {
					sender.sendMessage("§cFailed to save skin to file: " + e.getMessage());
					getLogger().log(Level.SEVERE, "Failed to save skin", e);
				}
				sender.sendMessage("§aSkin data saved.");
			});
			return;
		}

		try {
			URL url = new URL(urlString);
			boolean privateUpload = "true".equalsIgnoreCase(privateUploadString) || "yes".equalsIgnoreCase(privateUploadString) || "private".equalsIgnoreCase(privateUploadString);
			Model model = ("alex".equalsIgnoreCase(modelString) || "slim".equalsIgnoreCase(modelString)) ? Model.SLIM: Model.DEFAULT;

			if (skinFile.exists()) {
				sender.sendMessage("§cCustom skin '" + name + "' already exists. Please choose a different name.");
				return;
			} else {
				skinFile.createNewFile();
			}


			skinClient.generateUrl(url.toString(), SkinOptions.create(name, model, privateUpload ? Visibility.PRIVATE : Visibility.PUBLIC), new SkinCallback() {

				@Override
				public void waiting(long l) {
					sender.sendMessage("§7Waiting " + (l / 1000D) + "s to upload skin...");
				}

				@Override
				public void uploading() {
					sender.sendMessage("§eUploading skin...");
				}

				@Override
				public void error(String s) {
					sender.sendMessage("§cError while generating skin: " + s);
					sender.sendMessage("§cPlease make sure the image is a valid skin texture and try again.");

					skinFile.delete();
				}

				@Override
				public void exception(Exception exception) {
					sender.sendMessage("§cException while generating skin, see console for details: " + exception.getMessage());
					sender.sendMessage("§cPlease make sure the image is a valid skin texture and try again.");

					skinFile.delete();

					getLogger().log(Level.WARNING, "Exception while generating skin", exception);
				}

				@Override
				public void done(Skin skin) {
					sender.sendMessage("§aSkin data generated.");
					JsonObject jsonObject = new JsonObject();
					jsonObject.addProperty("id", skin.data.uuid.toString());
					jsonObject.addProperty("name", "");

					JsonObject property = new JsonObject();
					property.addProperty("name", "textures");
					property.addProperty("value", skin.data.texture.value);
					property.addProperty("signature", skin.data.texture.signature);

					JsonArray propertiesArray = new JsonArray();
					propertiesArray.add(property);

					jsonObject.add("properties", propertiesArray);

					try (Writer writer = new FileWriter(skinFile)) {
						new Gson().toJson(jsonObject, writer);
					} catch (IOException e) {
						sender.sendMessage("§cFailed to save skin to file: " + e.getMessage());
						getLogger().log(Level.SEVERE, "Failed to save skin", e);
					}
				}
			});
		} catch (MalformedURLException e) {
			sender.sendMessage("§cInvalid URL");
			return;
		} catch (IOException e) {
			sender.sendMessage("§cUnexpected IOException: " + e.getMessage());
			getLogger().log(Level.SEVERE, "Unexpected IOException while creating skin '" + name + "' with source '" + urlString + "'", e);
		}
	}

	@Command(name = "applycustomskin",
			 aliases = {
					 "setCustomSkin",
					 "applySkin" },
			 usage = "<Name> [Player]",
			 description = "Apply a previously generated skin",
			 min = 1,
			 max = 2,
			 fallbackPrefix = "customskins")
	@Permission("customskins.apply")
	public void applySkin(CommandSender sender, String name, String targetPlayer) {
		Player target;
		if (targetPlayer != null) {
			if (!sender.hasPermission("customskins.apply.other")) {
				sender.sendMessage("§cYou don't have permission to change other player's skins");
				return;
			}
			target = Bukkit.getPlayer(targetPlayer);
			if (target == null || !target.isOnline()) {
				sender.sendMessage("§cPlayer not found");
				return;
			}
		} else {
			if (sender instanceof Player) {
				target = (Player) sender;
			} else {
				sender.sendMessage("§cPlease specify the target player");
				return;
			}
		}

		File skinFile = new File(skinFolder, name + ".cs");
		if (!skinFile.exists()) {
			sender.sendMessage("§cSkin '" + name + "' does not exist");
			if (sender.hasPermission("customskins.create")) {
				sender.sendMessage("§cPlease use /createCustomSkin first");
			}
			return;
		}
		JsonObject skinData;
		try {
			skinData = new JsonParser().parse(new FileReader(skinFile)).getAsJsonObject();
		} catch (IOException e) {
			sender.sendMessage("§cFailed to load skin from file: " + e.getMessage());
			getLogger().log(Level.SEVERE, "Failed to load skin", e);
			return;
		}

		if (!loadedSkins.contains(name)) {
			NickNamerAPI.getNickManager().loadCustomSkin("cs_" + name, skinData);
			loadedSkins.add(name);
		}

		NickNamerAPI.getNickManager().setCustomSkin(target.getUniqueId(), "cs_" + name);
		sender.sendMessage("§aCustom skin changed to " + name);
	}

	@Command(name = "listcustomskins",
			 aliases = {
					 "listSkins" },
			 usage = "",
			 description = "Get a list of generated custom skins",
			 min = 0,
			 max = 0,
			 fallbackPrefix = "customskins")
	@Permission("customskins.list")
	public void listSkins(CommandSender sender) {
		for (String s : skinFolder.list()) {
			if (s.endsWith(".cs")) {
				sender.sendMessage(s);
			}
		}
	}

	@Completion(name = "applycustomskin")
	public void applySkin(List<String> completions, CommandSender sender, String name, String targetName) {
		if (sender.hasPermission("customskins.apply")) {
			if (name == null || name.isEmpty() || !new File(skinFolder, name + ".cs").exists()) {
				for (String s : skinFolder.list()) {
					if (s.endsWith(".cs")) {
						completions.add(s.substring(0, s.length() - 3));
					}
				}
			} else {
				if (sender.hasPermission("customskins.apply.other")) {
					for (Player player : Bukkit.getOnlinePlayers()) {
						completions.add(player.getName());
					}
				}
			}
		}
	}

}

