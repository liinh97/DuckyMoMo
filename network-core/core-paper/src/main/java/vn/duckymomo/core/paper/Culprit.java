package vn.duckymomo.core.paper;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Ai là người chơi chịu trách nhiệm cho một đòn đánh / cái chết: người chơi đánh trực tiếp, mũi tên/vật ném, đám mây thuốc, TNT,
 * thú thuần hoá (sói…) và pet MyPet (kể cả mũi tên pet bắn) đều tính về CHỦ. Dùng chung cho án Hoà Bình, treo thưởng, điểm Chiến Thần,
 * luật tài khoản phụ — để không thể dùng pet giết người mà né luật.
 */
final class Culprit {

    private Culprit() {
    }

    static Player of(Entity damager) {
        if (damager == null) {
            return null;
        }
        if (damager instanceof Player p) {
            return p;
        }
        Object src = null;
        if (damager instanceof Projectile pr) {
            src = pr.getShooter();
        } else if (damager instanceof AreaEffectCloud cloud) {
            src = cloud.getSource();
        } else if (damager instanceof TNTPrimed tnt) {
            src = tnt.getSource();
        } else if (damager instanceof Tameable pet && pet.getOwner() instanceof Player p) {
            return p;
        } else if (PetCombat.isPet(damager)) {
            return Arena.petOwner(damager);
        }
        if (src instanceof Player p) {
            return p;
        }
        if (src instanceof Entity e && PetCombat.isPet(e)) {
            return Arena.petOwner(e);
        }
        return null;
    }

    /** Người giết (theo đòn cuối), null nếu chết vì môi trường / quái. */
    static Player killer(Player victim) {
        return victim.getLastDamageCause() instanceof EntityDamageByEntityEvent last ? of(last.getDamager()) : null;
    }
}
