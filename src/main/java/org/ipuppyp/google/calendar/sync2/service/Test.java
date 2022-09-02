package org.ipuppyp.google.calendar.sync2.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Test {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("c:\\test\\2.txt");
        //Files.copy(Path.of("c:\\test\\1.txt"), path, REPLACE_EXISTING);

        BasicFileAttributeView view
                = Files.getFileAttributeView(
                path, BasicFileAttributeView.class);

        // method to read the file attributes.
        BasicFileAttributes attribute = view.readAttributes();

        // method to check the creation time of the file.
        System.out.print("Creation Time of the file: ");
        System.out.println(attribute.creationTime());
        System.out.print(
                "Last Accessed Time of the file: ");
        System.out.println(attribute.lastAccessTime());

        // method to check the last
        // modified time for the file
        System.out.print(
                "Last Modified Time for the file: ");
        System.out.println(attribute.lastModifiedTime());

        // method to access the check whether
        // the file is a directory or not.
        System.out.println("Directory or not: "
                + attribute.isDirectory());

        // method to access the size of the file in KB.
        System.out.println("Size of the file: "
                + attribute.size());

        Set<PosixFilePermission> filePerm = Files.getPosixFilePermissions(path);
        String permission = PosixFilePermissions.toString(filePerm);

        System.out.println(permission);

    }
}
