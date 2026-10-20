package vn.duckymomo.core.paper;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Thu/trả tiền qua Vault (VaultUnlocked + EssentialsX Economy ở banghoi). Tách lớp riêng: chỉ gọi khi available()
 * để server không có Vault không bị lỗi thiếu lớp.
 */
final class VaultEconomy {

    private VaultEconomy() {
    }

    static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("Vault") && economy() != null;
    }

    private static Economy economy() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    static double balance(OfflinePlayer p) {
        return economy().getBalance(p);
    }

    static boolean has(OfflinePlayer p, double amount) {
        return economy().has(p, amount);
    }

    static boolean withdraw(OfflinePlayer p, double amount) {
        return economy().has(p, amount) && economy().withdrawPlayer(p, amount).transactionSuccess();
    }

    static void deposit(OfflinePlayer p, double amount) {
        economy().depositPlayer(p, amount);
    }

    /** Đặt số dư về đúng amount (rút hết rồi nạp lại). */
    static void setBalance(OfflinePlayer p, double amount) {
        double bal = economy().getBalance(p);
        if (bal > 0) {
            economy().withdrawPlayer(p, bal);
        } else if (bal < 0) {
            economy().depositPlayer(p, -bal);
        }
        if (amount > 0) {
            economy().depositPlayer(p, amount);
        }
    }

    /** Tiền trong tin nhắn của DuckyMoMoCore: "4.000 xu" (Essentials để ký hiệu $ một ký tự, không ghi được "xu"). */
    static String format(double amount) {
        return Rewards.xu(amount);
    }
}
