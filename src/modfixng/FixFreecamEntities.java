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

package modfixng;

import java.util.HashMap;
import java.util.HashSet;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.comphenix.protocol.Packets;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

public class FixFreecamEntities implements Listener {
	
	private ModFixNG main;
	private Config config;
	
	FixFreecamEntities(ModFixNG main, Config config) {
		this.main = main;
		this.config = config;
		initClientCloseInventoryFixListener();
		initServerCloseInventoryFixListener();
		initEntitiesCheck();
	}
	
	private HashMap<String,Entity> playersopenedminecart = new HashMap<String,Entity>();
	
	//add player to list when he opens minecart
	@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
	public void onPlayerOpenedMinecart(PlayerInteractEntityEvent e)
	{
		if (!config.fixFreecamEntitiesEnabled)  {return;}
		
		if (config.fixFreecamEntitiesEntitiesIDs.contains(e.getRightClicked().getType().getTypeId()))
		{
			playersopenedminecart.put(e.getPlayer().getName(),e.getRightClicked());
		}
	}

	//remove player from list when he closes minecart
	private void initClientCloseInventoryFixListener()
	{
		main.protocolManager.addPacketListener(
				new PacketAdapter(
						PacketAdapter
						.params(main, Packets.Client.CLOSE_WINDOW)
						.clientSide()
						.listenerPriority(ListenerPriority.HIGHEST)
				) 
				{
					@Override
					public void onPacketReceiving(PacketEvent e) 
					{
						if (!config.fixFreecamEntitiesEnabled) {return;}
						
						if (e.getPlayer() == null) {return;}
						
						final String playername = e.getPlayer().getName();
						Bukkit.getScheduler().scheduleSyncDelayedTask(main, new Runnable()
						{
							public void run()
							{
								removePlayerFromList(playername);
							}
						});
					}
				});
	}
	private void initServerCloseInventoryFixListener()
	{
		main.protocolManager.addPacketListener(
				new PacketAdapter(
						PacketAdapter
						.params(main, Packets.Server.CLOSE_WINDOW)
						.serverSide()
						.listenerPriority(ListenerPriority.HIGHEST)
				) 
				{
					@Override
					public void onPacketSending(PacketEvent e) 
					{
						if (!config.fixFreecamEntitiesEnabled) {return;}
						
						removePlayerFromList(e.getPlayer().getName());
				    }
				});
	}
	@EventHandler(priority=EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e)
	{
		String playername = e.getPlayer().getName();
		if (playersopenedminecart.containsKey(playername))
		{
			removePlayerFromList(playername);
		}
	}
	private void removePlayerFromList(String playername)
	{
		playersopenedminecart.remove(playername);
	}


	//check if entity is not valid, is yes - force close inventory
	private void initEntitiesCheck()
	{
		Bukkit.getScheduler().scheduleSyncRepeatingTask(main, new Runnable()
		{
			public void run()
			{
				if (!config.fixFreecamEntitiesEnabled) {return;}
				
				HashSet<String> playerNamesToCheck = new HashSet<String>(playersopenedminecart.keySet());
				for (String playername : playerNamesToCheck)
				{
					Player player = Bukkit.getPlayerExact(playername);
					if (player != null)
					{
						Entity entity = playersopenedminecart.get(playername);
						if (!entity.isValid() || 
							!entity.getWorld().equals(player.getWorld()) ||
							entity.getLocation().distanceSquared(player.getLocation()) > 36
						)
						{
							playersopenedminecart.remove(playername);
							player.closeInventory();
						}
					}
				}
			}
		},0,1);
	}
	
}