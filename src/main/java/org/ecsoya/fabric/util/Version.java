package org.ecsoya.fabric.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Version {

	private int major;
	private int minor;

	public Version(String version) throws Exception {
		MajorMinor mm = parseVersion(version);
		this.major = mm.major;
		this.minor = mm.minor;
	}

	public void join(String version) throws Exception {
		MajorMinor nm = parseVersion(version);
		if (nm.major < major) {
			return;
		}
		this.major = Math.max(major, nm.major);
		this.minor = Math.max(minor, nm.minor);
	}

	public String nextVersion() {
		return major + "." + (minor + 1);
	}

	private static MajorMinor parseVersion(String version) throws Exception {
		if (version == null || version.equals("")) {
			throw new Exception("Version can not be empty");
		}
		int index = version.indexOf(".");
		if (index == -1) {
			throw new Exception(
					"Version format error: version should be [major].[minor], xxx and yyy are both numbers");
		}
		int major = Integer.parseInt(version.substring(0, index).trim());
		int minor = Integer.parseInt(version.substring(index + 1).trim());
		return new MajorMinor(major, minor);

	}

	private static class MajorMinor {

		int major;
		int minor;

		public MajorMinor(int major, int minor) {
			this.major = major;
			this.minor = minor;
		}

	}

	public static void main(String[] args) {
		try {
			Version version = new Version("2.0");

			String[] existVersions = { "1.1", "1.2", "1.5", "3.6" };

			List<String> versionList = new ArrayList<>(Arrays.asList(existVersions));
			for (int i = 0; i < 10; i++) {
				for (String ver : versionList) {
					version.join(ver);
				}
				String nextVersion = version.nextVersion();
				System.out.println(nextVersion);
				versionList.add(nextVersion);
			}

			System.out.println(Arrays.toString(versionList.toArray()));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
