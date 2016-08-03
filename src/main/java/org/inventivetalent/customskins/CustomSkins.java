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
import org.inventivetalent.nicknamer.api.NickNamerAPI;
import org.inventivetalent.pluginannotations.PluginAnnotations;
import org.inventivetalent.pluginannotations.command.Command;
import org.inventivetalent.pluginannotations.command.Completion;
import org.inventivetalent.pluginannotations.command.Permission;
import org.inventivetalent.skullclient.SkullCallback;
import org.inventivetalent.skullclient.SkullClient;
import org.inventivetalent.skullclient.SkullData;
import org.inventivetalent.update.spiget.SpigetUpdate;
import org.inventivetalent.update.spiget.UpdateCallback;
import org.mcstats.MetricsLite;

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

		try {
			MetricsLite metrics = new MetricsLite(this);
			if (metrics.start()) {
				getLogger().info("Metrics started");
			}
		} catch (Exception e) {
		}

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
			 usage = "<Name> <URL> [private]",
			 description = "Create a custom skin from the specified image url",
			 min = 2,
			 max = 3,
			 fallbackPrefix = "customskins")
	@Permission("customskins.create")
	public void createSkin(final CommandSender sender, String name, String urlString, String privateUploadString) {
		try {
			URL url = new URL(urlString);
			final File skinFile = new File(skinFolder, name + ".cs");
			boolean privateUpload = "true".equalsIgnoreCase(privateUploadString) || "yes".equalsIgnoreCase(privateUploadString) || "private".equalsIgnoreCase(privateUploadString);

			if (skinFile.exists()) {
				sender.sendMessage("§cCustom skin '" + name + "' already exists. Please choose a different name.");
				return;
			} else {
				skinFile.createNewFile();
			}

			SkullClient.create(url, privateUpload, new SkullCallback() {
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
				public void done(SkullData skullData) {
					sender.sendMessage("§aSkin data generated.");
					JsonObject jsonObject = new JsonObject();
					jsonObject.addProperty("id", skullData.getId().toString());
					jsonObject.addProperty("name", "");

					JsonObject property = new JsonObject();
					property.addProperty("name", "textures");
					property.addProperty("value", skullData.getProperties().firstTexture().getValue());
					property.addProperty("signature", skullData.getProperties().firstTexture().getSignature());

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

