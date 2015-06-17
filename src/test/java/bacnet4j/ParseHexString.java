package bacnet4j;

public class ParseHexString {

	public static void main(String[] args) {
		
		String hex = "0xBAC5";
		int dec = Integer.decode(hex);
		
		System.out.println("Dezimal: " + dec);

	}

}
