import type { ComposeNodeFactory, AlertDialogProps, DialogProps } from "../../examples/types/compose-dsl";
import type { JavaBridgeApi, JavaBridgePackage, JavaBridgeClass } from "../../examples/types/java-bridge";
import "../../examples/types/index";

declare const alert: ComposeNodeFactory<AlertDialogProps>;
declare const dialog: ComposeNodeFactory<DialogProps>;
alert({ title: "Title", onConfirm: async () => {}, closeOnConfirm: false });
dialog({ properties: { dismissOnBackPress: false } });
// @ts-expect-error Component properties retain their declared value types.
dialog({ closeOnDismissRequest: "yes" });
// @ts-expect-error Unknown properties are not accepted by the named component factory.
alert({ notAComponentProperty: 1 });

declare const java: JavaBridgeApi;
declare const namespace: JavaBridgePackage;
const className: string = java.type("java.lang.String").className;
const path: string = namespace.path;
const packageText: string = namespace.toString();
const nested: JavaBridgeClass | JavaBridgePackage = namespace.lang;
const exists: boolean = java.classExists(className);
// @ts-expect-error Known bridge methods retain argument validation.
java.type(123);
const icon: MaterialIconName = "Home";
void [path, packageText, nested, exists, icon];
