package me.disconnect.mobile.billing;

public interface BillingObserver {
	public void billingAvailable(boolean aAvailable);
	public void inventoryCompleted(Inventory inventory);
	public void purchaseComplete(boolean aSuccess);
}
