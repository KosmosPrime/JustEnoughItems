package mezz.jei.library.plugins.vanilla.ingredients;

import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.common.config.DebugConfig;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.common.util.StackHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ItemStackListFactory {
	private static final Logger LOGGER = LogManager.getLogger();

	public static List<ItemStack> create(StackHelper stackHelper) {
		final boolean debug = DebugConfig.isDebugIngredientsEnabled();

		final List<ItemStack> itemList = new ArrayList<>();
		final Set<Object> itemUidSet = new HashSet<>();

		Minecraft minecraft = Minecraft.getInstance();
		FeatureFlagSet features = Optional.ofNullable(minecraft.player)
				.map(p -> p.connection)
				.map(ClientPacketListener::enabledFeatures)
				.orElse(FeatureFlagSet.of());

		final boolean hasPermissions =
				minecraft.options.operatorItemsTab().get() ||
				Optional.of(minecraft)
				.map(m -> m.player)
				.map(Player::canUseGameMasterBlocks)
				.orElse(false);

		ClientLevel level = minecraft.level;
		if (level == null) {
			throw new NullPointerException("minecraft.level must be set before JEI fetches ingredients");
		}
		RegistryAccess registryAccess = level.registryAccess();
		final CreativeModeTab.ItemDisplayParameters displayParameters =
			new CreativeModeTab.ItemDisplayParameters(features, hasPermissions, registryAccess);

		for (CreativeModeTab itemGroup : CreativeModeTabs.allTabs()) {
			if (itemGroup.getType() != CreativeModeTab.Type.CATEGORY) {
				if (debug) {
					LOGGER.debug(
						"Skipping creative tab: '{}' because it is type: {}",
						itemGroup.getDisplayName().getString(),
						itemGroup.getType()
					);
				}
				continue;
			}
			try {
				itemGroup.buildContents(displayParameters);
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error(
					"Item Group crashed while building contents." +
					"Items from this group will be missing from the JEI ingredient list: {}",
					itemGroup.getDisplayName().getString(),
					e
				);
				continue;
			}

			@Unmodifiable Collection<ItemStack> creativeTabItemStacks;
			try {
				creativeTabItemStacks = itemGroup.getSearchTabDisplayItems();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error(
					"Item Group crashed while getting search tab display items." +
					"Some items from this group will be missing from the JEI ingredient list: {}",
					itemGroup.getDisplayName().getString(),
					e
				);
				continue;
			}

			if (creativeTabItemStacks.isEmpty()) {
				try {
					Collection<ItemStack> displayItems = itemGroup.getDisplayItems();
					if (displayItems.isEmpty()) {
						LOGGER.warn(
							"Item Group has no display items and no search tab display items. " +
							"Items from this group will be missing from the JEI ingredient list. {}",
							itemGroup.getDisplayName().getString()
						);
						continue;
					} else {
						LOGGER.warn(
							"Item Group has no search tab display items. " +
								"Falling back on getting the regular display items: {}",
							itemGroup.getDisplayName().getString()
						);
						creativeTabItemStacks = displayItems;
					}
				} catch (RuntimeException | LinkageError e) {
					LOGGER.error(
						"Item Group has no search tab display items and crashed while getting display items. " +
							"Items from this group will be missing from the JEI ingredient list. {}",
						itemGroup.getDisplayName().getString(),
						e
					);
					continue;
				}
			}

			int added = 0;
			for (ItemStack itemStack : creativeTabItemStacks) {
				if (itemStack.isEmpty()) {
					LOGGER.error("Found an empty itemStack from creative tab: {}", itemGroup);
				} else {
					if (addItemStack(stackHelper, itemStack, itemList, itemUidSet)) {
						added++;
					}
				}
			}
			if (debug) {
				LOGGER.debug(
					"Added {}/{} items from creative tab: {}",
					added,
					creativeTabItemStacks.size(),
					itemGroup.getDisplayName().getString()
				);
			}
		}
		return itemList;
	}

	private static boolean addItemStack(StackHelper stackHelper, ItemStack stack, List<ItemStack> itemList, Set<Object> itemUidSet) {
		final Object itemKey;

		if (stackHelper.hasSubtypes(stack)) {
			try {
				itemKey = stackHelper.getUniqueIdentifierForStack(stack, UidContext.Ingredient);
			} catch (RuntimeException | LinkageError e) {
				String stackInfo = ErrorUtil.getItemStackInfo(stack);
				LOGGER.error("Couldn't get unique name for itemStack {}", stackInfo, e);
				return false;
			}
		} else {
			itemKey = stack.getItem();
		}

		if (!itemUidSet.contains(itemKey)) {
			itemUidSet.add(itemKey);
			itemList.add(stack);
			return true;
		}
		return false;
	}

}
