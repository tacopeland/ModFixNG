/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */

package modfixng.fixes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import modfixng.main.Config;
import modfixng.main.ModFixNG;
import modfixng.nms.utils.NMSUtilsAccess;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public class ProperlyCloseEntitiesContainers implements Listener, Feature {

	private Config config;
	public ProperlyCloseEntitiesContainers(Config config) {
		this.config = config;
	}

	private final HashMap<Player, Entity> playerOpenEntity = new HashMap<Player, Entity>(200);

	private final HashSet<EntityType> knownEntityTypes  = new HashSet<EntityType>(
		Arrays.asList(
			new EntityType[] {
				//vanilla entities that has inventories
				EntityType.MINECART_CHEST,
				EntityType.MINECART_FURNACE,
				EntityType.MINECART_HOPPER,
				EntityType.VILLAGER,
				EntityType.HORSE
			}
		)
	);
	// add player to list when he opens entity inventory
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerOpenedEntity(PlayerInteractEntityEvent e) {
		Player player = e.getPlayer();

		if (playerOpenEntity.containsKey(player)) {
			if (NMSUtilsAccess.getNMSUtils().isInventoryOpen(player)) {
				e.setCancelled(true);
				return;
			}
		}

		final Entity entity = e.getRightClicked();
		if (config.properlyCloseEntitiesContainersEntitiesTypes.contains(entity.getType().toString()) || NMSUtilsAccess.getNMSUtils().hasInventory(entity) || knownEntityTypes.contains(entity.getType())) {
			playerOpenEntity.put(player, entity);
		}
	}

	//remove player from list when he closes inventory
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onInvetoryClose(InventoryCloseEvent event) {
		playerOpenEntity.remove(event.getPlayer());
	}
	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e) {
		playerOpenEntity.remove(e.getPlayer());
	}

	//close opened inventory on entity portal
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPortal(EntityPortalEvent event) {
		Entity entity = event.getEntity();
		Iterator<Entry<Player, Entity>> iterator = playerOpenEntity.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<Player, Entity> entry = iterator.next();
			if (entity.equals(entry.getValue())) {
				iterator.remove();
				entry.getKey().closeInventory();
			}
		}
	}

	//check valid on inventory click
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onClick(InventoryClickEvent event) {
		Player player = (Player) event.getWhoClicked();
		Entity entity = playerOpenEntity.get(player);
		if (entity != null) {
			if (!isValid(player, entity)) {
				event.setCancelled(true);
				player.closeInventory();
			}
		}
	}

	private BukkitTask task;
	// check if entity is not valid or player is too far away from it, if yes -  force close inventory
	private void initEntitiesCheck() {
		task = Bukkit.getScheduler().runTaskTimer(
			ModFixNG.getInstance(),
			new Runnable() {
				@Override
				public void run() {
					Iterator<Entry<Player, Entity>> iterator = playerOpenEntity.entrySet().iterator();
					while (iterator.hasNext()) {
						Entry<Player, Entity> entry = iterator.next();
						if (!isValid(entry.getKey(), entry.getValue())) {
							iterator.remove();
							entry.getKey().closeInventory();
						}
					}
				}
			},
			0, 1
		);
	}

	private boolean isValid(Player player, Entity entity) {
		return
		entity.isValid() &&
		NMSUtilsAccess.getNMSUtils().isInventoryValid(player) &&
		entity.getWorld().equals(player.getWorld()) &&
		entity.getLocation().distanceSquared(player.getLocation()) < 36;
	}

	@Override
	public void load() {
		Bukkit.getPluginManager().registerEvents(this, ModFixNG.getInstance());
		initEntitiesCheck();
	}

	@Override
	public void unload() {
		task.cancel();
		HandlerList.unregisterAll(this);
		Iterator<Entry<Player, Entity>> iterator = playerOpenEntity.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<Player, Entity> entry = iterator.next();
			iterator.remove();
			entry.getKey().closeInventory();
		}
	}

	@Override
	public String getName() {
		return "ProperlyCloseEntitiesContainers";
	}

}