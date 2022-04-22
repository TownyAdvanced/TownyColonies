package cz.neumimto.towny.townycolonies;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycolonies.config.ConfigurationService;
import cz.neumimto.towny.townycolonies.config.Structure;
import cz.neumimto.towny.townycolonies.db.Database;
import cz.neumimto.towny.townycolonies.mechanics.RequirementMechanic;
import cz.neumimto.towny.townycolonies.mechanics.TownContext;
import cz.neumimto.towny.townycolonies.model.LoadedStructure;
import cz.neumimto.towny.townycolonies.model.StructureAndCount;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class StructureService {

    private Map<UUID, LoadedStructure> structures = new HashMap<>();
    private Map<UUID, Set<LoadedStructure>> byTown = new HashMap<>();

    @Inject
    private ConfigurationService configurationService;

    @Inject
    private SubclaimService subclaimService;

    public Collection<LoadedStructure> getAllStructures() {
        return structures.values();
    }

    public Collection<LoadedStructure> getAllStructures(Town town) {
        return byTown.getOrDefault(town.getUUID(), Collections.emptySet());
    }

    public void isValidLocation(Structure structure, Location location) {

    }

    public void addToTown(Structure structure, Town town, Location location) {

    }

    public ItemStack toItemStack(Structure structure, Town context, int count) {
        ItemStack itemStack = new ItemStack(structure.material);
        ItemMeta itemMeta = itemStack.getItemMeta();

        var mm = MiniMessage.miniMessage();
        itemMeta.displayName(mm.deserialize(structure.name));
        itemMeta.setCustomModelData(structure.customModelData);

        List<Component> lore = configurationService.buildStructureLore(structure, count, structure.maxCount, context);
        itemMeta.lore(lore);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public List<StructureAndCount> findTownStructures(Town town) {
        Collection<Structure> allStructures = configurationService.getAll();
        Collection<LoadedStructure> townStructures = getAllStructures(town);

        Map<Structure, Integer> alreadyBuilt = new HashMap<>();
        List<Structure> avalaible = new ArrayList<>();

        for (Structure structure : allStructures) {
            boolean found = false;
            for (LoadedStructure townStructure : townStructures) {
                if (townStructure.structure == structure) {
                    alreadyBuilt.merge(structure, 1, Integer::sum);
                    found = true;
                }
            }

            if (!found) {
                avalaible.add(structure);
            }
        }

        List<StructureAndCount> merged = new ArrayList<>();
        for (Map.Entry<Structure, Integer> entry : alreadyBuilt.entrySet()) {
            merged.add(new StructureAndCount(entry.getKey(), entry.getValue()));
        }
        avalaible.sort(Comparator.comparing(o -> o.name));

        for (Structure structure : avalaible) {
            merged.add(new StructureAndCount(structure, 0));
        }

        return merged;
    }

    public StructureAndCount findTownStructureById(Town town, Structure structure) {
        Collection<LoadedStructure> townStructures = getAllStructures(town);
        int count = 0;
        for (LoadedStructure townStructure : townStructures) {
            if (townStructure.structure == structure) {
                count++;
            }
        }
        return new StructureAndCount(structure, count);
    }

    public ItemStack buyBlueprint(TownContext townContext) {
        for (Structure.LoadedPair<RequirementMechanic<?>, ?> requirement : townContext.structure.buyRequirements) {
            Object configValue = requirement.configValue;
            var mechanic = (RequirementMechanic<Object>) requirement.mechanic;
            mechanic.postAction(townContext, configValue);
            mechanic.okmessage(townContext, configValue);
        }
        return toBlueprintItemStack(townContext.structure);
    }

    private ItemStack toBlueprintItemStack(Structure structure) {
        ItemStack itemStack = new ItemStack(structure.material);
        itemStack.editMeta(itemMeta -> {
            itemMeta.displayName(Component.text("Blueprint - " + structure.name));
            itemMeta.setCustomModelData(structure.customModelData);
        });
        return itemStack;
    }


    public boolean canBuy(TownContext context) {
        boolean pass = true;
        for (Structure.LoadedPair<RequirementMechanic<?>, ?> requirement : context.structure.buyRequirements) {
            Object configValue = requirement.configValue;
            var mechanic = (RequirementMechanic<Object>) requirement.mechanic;
            if (!mechanic.check(context, configValue)) {
                mechanic.nokmessage(context, configValue);
                pass = false;
            }
        }
        return pass;
    }

    public void addToTown(Town town, LoadedStructure loadedStructure) {

    }

    public void loadAll() {
        structures.clear();
        Database.init();
        Collection<LoadedStructure> loaded = Database.allStructures();
        Collection<UUID> towns = TownyAPI.getInstance().getTowns().stream().map(Town::getUUID).collect(Collectors.toSet());
        loaded.stream()
                .peek(a -> a.structure = configurationService.findStructureById(a.strucutureId).orElse(null))
                .filter(a -> a.structure != null)
                .filter(a->towns.contains(a.town))
                .peek(a -> structures.put(a.uuid, a))
                .forEach(a -> {
                    Set<LoadedStructure> set = new HashSet<>();
                    set.add(a);
                    byTown.merge(a.town, set, (loadedStructures, loadedStructures2) -> {
                        loadedStructures.addAll(loadedStructures2);
                        return loadedStructures;
                    });
                });
    }

    public void saveAll() {
        Database.saveAll(structures.values());
    }
}
