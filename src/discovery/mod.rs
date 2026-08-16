pub mod mdns;
pub mod pairing;

pub use mdns::{
    is_version_compatible, DiscoveredPeer, PeerBeacon, PeerDiscovery, CURRENT_APP_VERSION,
    MIN_SUPPORTED_APP_VERSION,
};
pub use pairing::{PairingManager, PairingSession};
