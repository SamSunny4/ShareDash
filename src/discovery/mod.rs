pub mod bluetooth;
pub mod mdns;
pub mod pairing;

pub use bluetooth::{BleDiscovery, DiscoveryMethod};
pub use mdns::{
    is_version_compatible, DiscoveredPeer, PeerBeacon, PeerDiscovery, WifiCapsInfo, CURRENT_APP_VERSION,
    MIN_SUPPORTED_APP_VERSION,
};
pub use pairing::{PairingManager, PairingSession};

