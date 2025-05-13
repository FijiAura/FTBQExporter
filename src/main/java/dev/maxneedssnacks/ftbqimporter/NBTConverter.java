package dev.maxneedssnacks.ftbqexporter;

import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.io.DataWriter;
import com.feed_the_beast.ftblib.lib.util.NBTConverter;
import com.feed_the_beast.ftbquests.quest.Chapter;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.ServerQuestFile;
import com.feed_the_beast.ftbquests.quest.loot.LootCrate;
import com.feed_the_beast.ftbquests.quest.loot.RewardTable;
import com.feed_the_beast.ftbquests.quest.loot.WeightedReward;
import com.feed_the_beast.ftbquests.quest.reward.ItemReward;
import com.feed_the_beast.ftbquests.quest.reward.Reward;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.util.ServerQuestData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class CommandExport extends CommandBase {

    @Override
    public String getName() {
        return "ftbq_export";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ftbq_export <quests|progress> [-c, -d]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }

        switch (args[0].toLowerCase()) {
            case "q":
            case "quests":
                exportQuests(server, sender, Arrays.asList(args).subList(1, args.length));
                break;
            case "p":
            case "progress":
                exportProgress(server, sender, Arrays.asList(args).subList(1, args.length));
                break;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    public void exportQuests(MinecraftServer server, ICommandSender sender, List<String> flags) throws CommandException {
        sender.sendMessage(new TextComponentString("Exporting Quests..."));

        ServerQuestFile f = ServerQuestFile.INSTANCE;
        JsonObject defaultQuestsJson = new JsonObject();
        JsonArray questLinesArray = new JsonArray();

        // Convert FTB Quests data to Better Questing format
        for (Chapter chapter : f.chapters) {
            JsonObject chapterJson = new JsonObject();
            chapterJson.addProperty("name", chapter.title);
            chapterJson.addProperty("desc", String.join("\n", chapter.subtitle));

            JsonObject chapterProperties = new JsonObject();
            chapterProperties.add("betterquesting", chapterJson);

            JsonObject chapterNbt = new JsonObject();
            chapterNbt.add("properties", chapterProperties);

            JsonArray questsArray = new JsonArray();
            for (Quest quest : chapter.quests) {
                JsonObject questJson = new JsonObject();
                questJson.addProperty("name", quest.title);
                questJson.addProperty("desc", String.join("\n", quest.description));

                JsonArray tasksArray = new JsonArray();
                for (Task task : quest.tasks) {
                    JsonObject taskJson = new JsonObject();
                    // Add task details to taskJson
                    tasksArray.add(taskJson);
                }
                questJson.add("tasks", tasksArray);

                JsonArray rewardsArray = new JsonArray();
                for (Reward reward : quest.rewards) {
                    JsonObject rewardJson = new JsonObject();
                    // Add reward details to rewardJson
                    rewardsArray.add(rewardJson);
                }
                questJson.add("rewards", rewardsArray);

                questsArray.add(questJson);
            }
            chapterNbt.add("quests", questsArray);

            questLinesArray.add(chapterNbt);
        }

        defaultQuestsJson.add("questLines", questLinesArray);

        // Export loot
        LootExporter lootExporter = new LootExporter(f);
        JsonObject lootJson = lootExporter.exportLoot();
        defaultQuestsJson.add("loot", lootJson);

        // Save to JSON file
        DataWriter.save(new File(Loader.instance().getConfigDir(), "exported_quests.json"), defaultQuestsJson);

        sender.sendMessage(new TextComponentString("Finished exporting Quests!"));
    }

    public void exportProgress(MinecraftServer server, ICommandSender sender, List<String> flags) throws CommandException {
        sender.sendMessage(new TextComponentString("Exporting Progress..."));

        final Universe u = Universe.get();
        JsonObject questProgressJson = new JsonObject();
        JsonArray questProgressArray = new JsonArray();

        // Convert FTB Quests progress data to Better Questing format
        for (ForgeTeam team : u.getTeams()) {
            ServerQuestData teamData = ServerQuestData.get(team);
            for (ForgePlayer player : team.getMembers()) {
                JsonObject playerProgress = new JsonObject();

                JsonArray completedQuestsArray = new JsonArray();
                for (Quest quest : ServerQuestFile.INSTANCE.quests) {
                    if (teamData.isCompleted(quest)) {
                        JsonObject questProgress = new JsonObject();
                        questProgress.addProperty("claimed", teamData.isRewardClaimed(player.getId(), quest.rewards.get(0)));
                        questProgress.addProperty("questID", quest.id);
                        completedQuestsArray.add(questProgress);
                    }
                }
                playerProgress.add("completed", completedQuestsArray);

                JsonArray completedTasksArray = new JsonArray();
                for (Task task : ServerQuestFile.INSTANCE.tasks) {
                    if (teamData.isCompleted(task)) {
                        JsonObject taskProgress = new JsonObject();
                        taskProgress.addProperty("index", task.id);
                        completedTasksArray.add(taskProgress);
                    }
                }
                playerProgress.add("tasks", completedTasksArray);

                JsonObject playerProgressWrapper = new JsonObject();
                playerProgressWrapper.addProperty("uuid", player.getId().toString());
                playerProgressWrapper.add("questProgress", playerProgress);
                questProgressArray.add(playerProgressWrapper);
            }
        }

        questProgressJson.add("questProgress", questProgressArray);

        // Save to JSON file
        DataWriter.save(new File(u.getWorldDirectory(), "exported_progress.json"), questProgressJson);

        sender.sendMessage(new TextComponentString("Finished exporting Progress!"));
    }
}

class LootExporter {

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
