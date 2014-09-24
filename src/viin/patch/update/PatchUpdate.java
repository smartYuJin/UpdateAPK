package viin.patch.update;

public class PatchUpdate {

	static{
		System.loadLibrary("update");
	}
	
	public	native	int	patch(String oldApkPath,	String	newApkPath,	String	patchPath);
}
