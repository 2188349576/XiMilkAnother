package cn.hairuosky.ximilkanother;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class XiMilkAnother extends JavaPlugin implements Listener {

    private double milkChance;
    private double damageAmount;
    private boolean debugMode;
    private String milkBucketName;
    private String messagePrefix;
    private int cooldownSeconds;

    // 消息
    private String messageCooldown;
    private String messageMilkSuccess;
    private String messageMilkFail;

    // 记录每个玩家的冷却时间
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        milkChance = config.getDouble("milk-chance", 1.0);
        damageAmount = config.getDouble("damage-amount", 1.0);
        debugMode = config.getBoolean("debug-mode", false);
        milkBucketName = config.getString("milk-bucket-name", "%target%的奶");
        messagePrefix = config.getString("message-prefix", "[MilkPlugin]");
        cooldownSeconds = config.getInt("cooldown-seconds", 10);

        // 读取自定义消息
        messageCooldown = config.getString("messages.cooldown", "你必须等待 %time% 秒后才能再次挤奶。");
        messageMilkSuccess = config.getString("messages.milk-success", "你从 %target% 挤了一桶奶！");
        messageMilkFail = config.getString("messages.milk-fail", "你未能成功挤到奶。");

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("XiMilkAnother 已启动！");
    }

    @Override
    public void onDisable() {
        getLogger().info("XiMilkAnother 已停止！");
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Player) {
            Player player = event.getPlayer();
            Player targetPlayer = (Player) event.getRightClicked();

            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
                return; // 仅处理主手点击
            }

            UUID playerUUID = player.getUniqueId();
            long currentTime = System.currentTimeMillis();

            // 检查冷却时间
            if (cooldowns.containsKey(playerUUID) && (currentTime - cooldowns.get(playerUUID)) < cooldownSeconds * 1000L) {
                long timeLeft = cooldownSeconds - ((currentTime - cooldowns.get(playerUUID)) / 1000);
                player.sendMessage(messagePrefix + " " + messageCooldown.replace("%time%", String.valueOf(timeLeft)));
                return;
            }

            ItemStack mainHandItem = player.getInventory().getItemInMainHand();

            if (mainHandItem.getType() == Material.BUCKET) {
                if (debugMode) {
                    getLogger().info(player.getName() + " 正在尝试从 " + targetPlayer.getName() + " 挤奶。");
                    getLogger().info("当前空桶数量: " + mainHandItem.getAmount());
                }

                if (new Random().nextDouble() <= milkChance) {
                    mainHandItem.setAmount(mainHandItem.getAmount() - 1);

                    if (mainHandItem.getAmount() <= 0) {
                        player.getInventory().setItemInMainHand(null);
                        if (debugMode) getLogger().info("空桶已消耗完毕，清除主手物品。");
                    } else {
                        player.getInventory().setItemInMainHand(mainHandItem);
                        if (debugMode) getLogger().info("更新后的空桶数量: " + mainHandItem.getAmount());
                    }

                    ItemStack milkBucket = new ItemStack(Material.MILK_BUCKET);
                    ItemMeta meta = milkBucket.getItemMeta();
                    if (meta != null) {
                        String customName = milkBucketName.replace("%target%", targetPlayer.getName());
                        meta.setDisplayName(customName);
                        milkBucket.setItemMeta(meta);
                    }

                    player.getInventory().addItem(milkBucket);
                    player.sendMessage(messagePrefix + " " + messageMilkSuccess.replace("%target%", targetPlayer.getName()));
                    if (debugMode) getLogger().info("已添加 " + targetPlayer.getName() + " 的奶 到 " + player.getName() + " 的物品栏。");

                    DustOptions dustOptions = new DustOptions(org.bukkit.Color.fromRGB(255, 255, 255), 1.0F);
                    targetPlayer.getWorld().spawnParticle(
                            Particle.REDSTONE,
                            targetPlayer.getLocation(),
                            10,
                            0.5, 0.5, 0.5,
                            0.1,
                            dustOptions
                    );

                    targetPlayer.damage(damageAmount);
                    if (debugMode) getLogger().info("对 " + targetPlayer.getName() + " 造成了 " + damageAmount + " 点伤害。");

                    // 更新玩家的最后挤奶时间
                    cooldowns.put(playerUUID, currentTime);
                } else {
                    player.sendMessage(messagePrefix + " " + messageMilkFail);
                    if (debugMode) getLogger().info(player.getName() + " 未能从 " + targetPlayer.getName() + " 挤到奶。");
                }

                event.setCancelled(true);
                if (debugMode) getLogger().info("事件已取消，以防止进一步处理。");
            }
        }
    }
}
