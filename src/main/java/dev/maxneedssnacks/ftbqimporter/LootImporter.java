package dev.maxneedssnacks.ftbqexporter;

import com.feed_the_beast.ftblib.lib.util.NBTConverter;
import com.feed_the_beast.ftblib.lib.util.StringUtils;
import com.feed_the_beast.ftbquests.quest.ServerQuestFile;
import com.feed_the_beast.ftbquests.quest.loot.LootCrate;
import com.feed_the_beast.ftbquests.quest.loot.RewardTable;
import com.feed_the_beast.ftbquests.quest.loot.WeightedReward;
import com.feed_the_beast.ftbquests.quest.reward.ItemReward;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class LootExporter {

    private final ServerQuestFile questFile;

    public LootExporter(ServerQuestFile questFile) {
        this.questFile = questFile;
    }

    public JsonObject exportLoot() {
        JsonObject lootJson = new JsonObject();
        JsonArray groupsArray = new JsonArray();

        for (RewardTable table : questFile.rewardTables) {
            if (table.lootCrate != null) {
                JsonObject groupJson = new JsonObject();
                groupJson.addProperty("name", table.title);
                groupJson.addProperty("weight", 1); // Default weight

                JsonArray rewardsArray = new JsonArray();
                for (WeightedReward reward : table.rewards) {
                    if (reward.reward instanceof ItemReward) {
                        ItemReward itemReward = (ItemReward) reward.reward;
                        JsonObject rewardJson = new JsonObject();
                        rewardJson.addProperty("weight", reward.weight);

                        JsonArray itemsArray = new JsonArray();
                        JsonObject itemJson = new JsonObject();
                        NBTTagCompound itemTag = new NBTTagCompound();
                        itemReward.getItem().writeToNBT(itemTag);
                        itemJson.addProperty("item", itemTag.toString());
                        itemsArray.add(itemJson);

                        rewardJson.add("items", itemsArray);
                        rewardsArray.add(rewardJson);
                    }
                }

                groupJson.add("rewards", rewardsArray);
                groupsArray.add(groupJson);
            }
        }

        lootJson.add("groups", groupsArray);
        return lootJson;
    }

    public NBTTagCompound exportLootToNBT() {
        JsonObject lootJson = exportLoot();
        return NBTConverter.JSONtoNBT_Object(lootJson, new NBTTagCompound(), true);
    }
}
