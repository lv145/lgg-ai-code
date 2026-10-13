package com.lgg.lggaicode.utils;

import java.io.File;
import java.io.FileFilter;
import java.util.Comparator;

public class DirectoryTreePrinterUtils {


    /**
     * 目录树结构
     *
     * @param directory  目录路径
     * @param prefix     前缀字符串
     * @param isLast     是否是最后一个子节点
     * @param fileFilter 需要过滤的文件
     */
    public static String directoryTree(String directory, String prefix, boolean isLast, FileFilter fileFilter) {
        StringBuilder sb = new StringBuilder();
        File dir = new File(directory);
        if (!dir.exists() || !dir.isDirectory()) {
            return dir.getName() + " 目录不存在或不是有效目录";
        }
        File[] files = dir.listFiles(fileFilter);
//        File[] files = dir.listFiles();
        if (files == null) return "";

        // 打印当前目录或文件
//        System.out.println(prefix + (isLast ? "└── " : "├── ") + dir.getName());
        sb.append(prefix + (isLast ? "└── " : "├── ") + dir.getName() + "\n");

        // 排序：先目录后文件，按名称排序
        java.util.Arrays.sort(files, Comparator
                .comparing((File f) -> !f.isDirectory())  // 目录在前
                .thenComparing(File::getName));

        for (int i = 0; i < files.length; i++) {
            boolean last = (i == files.length - 1);
            String newPrefix = prefix + (isLast ? "    " : "│   ");
            if (files[i].isDirectory()) {
                // 递归处理子目录
                sb.append(directoryTree(files[i].getPath(), newPrefix, last, fileFilter));
            } else {
                // 处理文件
                sb.append(newPrefix + (last ? "└── " : "├── ") + files[i].getName());
            }
            if (i != files.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }


    public static String directoryTree(String directory, boolean isLast) {
        return directoryTree(directory, "", isLast, null);
    }

    /**
     * 目录树结构
     * @param directory
     * @param fileFilter
     * @return
     */
    public static String directoryTree(String directory, FileFilter fileFilter) {
        return directoryTree(directory, "", false, fileFilter);
    }

    public static String directoryTree(String directory) {
        return directoryTree(directory, "", false, null);
    }

}
