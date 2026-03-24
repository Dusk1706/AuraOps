package com.auraops.operator.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("auraops.io")
@Version("v1")
@Kind("HealerPolicy")
@Plural("healerpolicies")
@Singular("healerpolicy")
@ShortNames("hp")
public class HealerPolicy extends CustomResource<HealerPolicySpec, HealerPolicyStatus> implements Namespaced {
}
