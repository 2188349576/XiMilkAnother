package cn.hairuosky.ximilkanother;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public final class XiMilkAnother extends JavaPlugin implements Listener, CommandExecutor {

    private double milkChance;
    private double damageAmount;
    private boolean debugMode;
    private String milkBucketName;
    private String messagePrefix;
    private String consume;
    private int cooldownSeconds;
    private double damageChance; // 新增的扣血概率
    // 在 XiMilkAnother 类中
    private List<PotionEffect> potionEffects = new ArrayList<>();
    // 消息
    private String messageCooldown;
    private String messageMilkSuccess;
    private String messageMilkFail;

    // 记录每个玩家的冷却时间
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(this.getCommand("ximilkanother")).setExecutor(this);
        Objects.requireNonNull(this.getCommand("ximilkanother")).setTabCompleter(new XiMilkAnotherTabCompleter()); // 注册 TabCompleter
        getLogger().info(messagePrefix + " XiMilkAnother 已启动！");
    }

    @Override
    public void onDisable() {
        getLogger().info(messagePrefix + " XiMilkAnother 已停止！");
    }

    private void loadConfigValues() {
        FileConfiguration config = getConfig();
        milkChance = config.getDouble("milk-chance", 1.0);
        damageAmount = config.getDouble("damage-amount", 1.0);
        debugMode = config.getBoolean("debug-mode", false);
        milkBucketName = config.getString("milk-bucket-name", "&e&l%target%&2的奶");
        messagePrefix = ChatColor.translateAlternateColorCodes('&', config.getString("message-prefix", "&7[&eXiMilkAnother&7]"));
        cooldownSeconds = config.getInt("cooldown-seconds", 10);
        damageChance = config.getDouble("damage-chance", 0.5); // 读取扣血概率


        // 读取自定义消息并转换颜色
        messageCooldown = ChatColor.translateAlternateColorCodes('&', config.getString("messages.cooldown", "&2你必须等待 &e&l%time% &2秒后才能再次挤奶。"));
        messageMilkSuccess = ChatColor.translateAlternateColorCodes('&', config.getString("messages.milk-success", "&2你从 &e&l%target% &2挤了一桶奶！"));
        messageMilkFail = ChatColor.translateAlternateColorCodes('&', config.getString("messages.milk-fail", "&4你未能成功挤到奶。"));
        consume = ChatColor.translateAlternateColorCodes('&',config.getString("messages.consume","&2你喝下了特殊的奶，获得了药水效果"));
        // 读取多个药水效果配置
        potionEffects.clear();
        List<Map<?, ?>> effectsConfig = config.getMapList("potion-effects");
        for (Map<?, ?> effectConfig : effectsConfig) {
            String effectTypeName = (String) effectConfig.get("type");

            // 使用 Number 来处理可能的转换问题
            Number durationNumber = (Number) effectConfig.get("duration");
            Number amplifierNumber = (Number) effectConfig.get("amplifier");

            int duration = durationNumber != null ? durationNumber.intValue() : 0;
            int amplifier = amplifierNumber != null ? amplifierNumber.intValue() : 0;

            PotionEffectType effectType = PotionEffectType.getByName(effectTypeName.toUpperCase());
            if (effectType != null) {
                PotionEffect potionEffect = new PotionEffect(effectType, duration, amplifier);
                potionEffects.add(potionEffect);
            } else {
                getLogger().warning("未知的药水效果类型: " + effectTypeName);
            }
        }
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
                    getLogger().info(messagePrefix + " " + player.getName() + " 正在尝试从 " + targetPlayer.getName() + " 挤奶。");
                    getLogger().info(messagePrefix + " 当前空桶数量: " + mainHandItem.getAmount());
                }

                if (new Random().nextDouble() <= milkChance) {
                    mainHandItem.setAmount(mainHandItem.getAmount() - 1);

                    if (mainHandItem.getAmount() <= 0) {
                        player.getInventory().setItemInMainHand(null);
                        if (debugMode) getLogger().info(messagePrefix + " 空桶已消耗完毕，清除主手物品。");
                    } else {
                        player.getInventory().setItemInMainHand(mainHandItem);
                        if (debugMode) getLogger().info(messagePrefix + " 更新后的空桶数量: " + mainHandItem.getAmount());
                    }

                    ItemStack milkBucket = new ItemStack(Material.MILK_BUCKET);
                    ItemMeta meta = milkBucket.getItemMeta();
                    if (meta != null) {
                        // 将配置中的颜色符号转换为实际的颜色代码
                        String customName = ChatColor.translateAlternateColorCodes('&', milkBucketName.replace("%target%", targetPlayer.getName()));
                        meta.setDisplayName(customName);

                        // 添加附魔光效（显示光效，但实际没有附魔效果）
                        meta.addEnchant(Enchantment.DURABILITY, 1, true); // 示例附魔光效
                        // 隐藏附魔的实际效果
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); // 隐藏附魔效果
                        // 设置自定义标签（用于标记特殊奶）
                        PersistentDataContainer dataContainer = meta.getPersistentDataContainer();
                        dataContainer.set(new NamespacedKey(this, "special_milk"), PersistentDataType.BYTE, (byte) 1); // 自定义标签

                        milkBucket.setItemMeta(meta);
                    }

                    player.getInventory().addItem(milkBucket);
                    player.sendMessage(messagePrefix + " " + messageMilkSuccess.replace("%target%", targetPlayer.getName()));
                    if (debugMode) getLogger().info(messagePrefix + " 已添加 " + targetPlayer.getName() + " 的奶 到 " + player.getName() + " 的物品栏。");

                    // 生成白色粒子效果
                    DustOptions whiteDustOptions = new DustOptions(org.bukkit.Color.fromRGB(255, 255, 255), 1.0F);
                    targetPlayer.getWorld().spawnParticle(
                            Particle.REDSTONE,
                            targetPlayer.getLocation(),
                            10,
                            0.5, 0.5, 0.5,
                            0.1,
                            whiteDustOptions
                    );

                    // 基于概率扣血并生成红色粒子效果
                    if (new Random().nextDouble() <= damageChance) {
                        targetPlayer.damage(damageAmount);
                        if (debugMode) getLogger().info(messagePrefix + " 对 " + targetPlayer.getName() + " 造成了 " + damageAmount + " 点伤害。");

                        // 生成红色粒子效果
                        DustOptions redDustOptions = new DustOptions(org.bukkit.Color.fromRGB(255, 0, 0), 1.0F);
                        targetPlayer.getWorld().spawnParticle(
                                Particle.REDSTONE,
                                targetPlayer.getLocation(),
                                10,
                                0.5, 0.5, 0.5,
                                0.1,
                                redDustOptions
                        );
                    }

                    // 更新玩家的最后挤奶时间
                    cooldowns.put(playerUUID, currentTime);
                } else {
                    player.sendMessage(messagePrefix + " " + messageMilkFail);
                    if (debugMode) getLogger().info(messagePrefix + " " + player.getName() + " 未能从 " + targetPlayer.getName() + " 挤到奶。");
                }

                event.setCancelled(true);
                if (debugMode) getLogger().info(messagePrefix + " 事件已取消，以防止进一步处理。");
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("ximilkanother")) {
            // 处理 reload 子命令
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("ximilkanother.reload")) {
                    reloadConfig();
                    loadConfigValues();
                    sender.sendMessage(messagePrefix + " 配置已重新加载！");
                } else {
                    sender.sendMessage(messagePrefix + " 你没有权限执行此命令。");
                }
                return true;
            }

            // 处理 test 子命令
            if (args.length > 0 && args[0].equalsIgnoreCase("test")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;

                    Effect(player);
                    // 输出玩家当前药水效果
                    getLogger().info("玩家 " + player.getName() + " 通过 /ximilkanother test 命令应用药水效果: ");

                    player.sendMessage(messagePrefix + " 你通过 /ximilkanother test 命令获得了速度药水效果！");
                } else {
                    sender.sendMessage(messagePrefix + " 只有玩家才能使用此命令！");
                }
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        // 确认玩家是否喝下了奶桶
        if (item.getType() == Material.MILK_BUCKET) {
            player.sendMessage(messagePrefix + consume);
            // 使用 BukkitScheduler 创建一个延迟任务，稍微延迟后再应用药水效果
            getServer().getScheduler().runTaskLater(this, () -> {
                // 应用药水效果
                Effect(player);

            }, 2L); // 延迟1秒（20 ticks = 1秒）
        }
    }

    public void Effect(Player player) {
        // 遍历并应用所有配置的药水效果
        for (PotionEffect effect : potionEffects) {
            player.addPotionEffect(effect);
        }
    }
}
